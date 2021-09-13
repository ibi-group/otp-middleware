package org.opentripplanner.middleware.cdp;

import com.google.common.collect.Sets;
import org.opentripplanner.middleware.models.TripHistoryUpload;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.S3Exception;
import org.opentripplanner.middleware.utils.S3Utils;
import org.opentripplanner.middleware.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN;
import static org.opentripplanner.middleware.utils.DateTimeUtils.convertToLocalDate;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getDateMinusNumberOfDays;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getDatePlusNumberOfDays;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getEndOfDay;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getStartOfDay;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getStringFromDate;

public class ConnectedDataManager {

    // If set to true, no files are upload to S3 or deleted from the local disk (this will be carried out by the unit
    // tests instead).
    public static boolean IS_TEST;

    public static final String FILE_NAME_SUFFIX = "anon-trip-data";
    public static final String ZIP_FILE_NAME_SUFFIX = FILE_NAME_SUFFIX + ".zip";
    public static final String DATA_FILE_NAME_SUFFIX = FILE_NAME_SUFFIX + ".json";

    private static final int CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES =
        getConfigPropertyAsInt("CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES", 5);

    private static final Logger LOG = LoggerFactory.getLogger(ConnectedDataManager.class);

    public static final String CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME =
        getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME");

    public static final String CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME =
        getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME");

    public static void scheduleTripHistoryUploadJob() {
        LOG.info("Scheduling trip history upload for every {} minute(s)", CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES);
        Scheduler.scheduleJob(
            new TripHistoryUploadJob(),
            0,
            CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES,
            TimeUnit.MINUTES);
    }

    /**
     * Remove a user's trip requests and trip summaries from the database. Record the user's trip dates so that all
     * data previously uploaded to s3 can be recompiled and uploaded again.
     */
    public static void removeUsersTripHistory(String userId) {
        Set<Date> userTripDates = new HashSet<>();
        for (TripRequest request : TripRequest.requestsForUser(userId)) {
            userTripDates.add(getStartOfDay(request.dateCreated));
            // This will delete the trip summaries as well.
            request.delete();
        }
        // Get all dates that have already been earmarked for uploading.
        Set<Date> incompleteUploads = new HashSet<>();
        getIncompleteUploads().forEach(tripHistoryUpload -> incompleteUploads.add(tripHistoryUpload.uploadDate));
        // Save all new dates for uploading.
        Set<Date> newDates = Sets.difference(userTripDates, incompleteUploads);
        TripHistoryUpload first = TripHistoryUpload.getFirst();
        newDates.forEach(newDate -> {
            if (first != null &&
                (newDate.getTime() == first.uploadDate.getTime() || newDate.after(first.uploadDate) &&
                newDate.before(getStartOfDay(new Date())))
            ) {
                // If the new date is the same or after the first ever upload date, add it to the upload list. This acts
                // as a back stop to prevent historic uploads being created indefinitely. Also, make sure the new date
                // is before today because the trip data for it isn't uploaded until after midnight.
                Persistence.tripHistoryUploads.create(new TripHistoryUpload(newDate));
            }
        });
        // Kick-off trip data processing to recompile and upload trip data to S3 minus the user's trip history.
        processTripHistory();
    }

    /**
     * Get all trip request and trip summary data between the provided dates. Anonymize the data and serialize to JSON.
     */
    public static String getAnonymizedTripHistory(Date start, Date end) {
        return JsonUtils.toJson(
            new TripHistory(
                TripRequest.getAnonymizedTripRequests(start, end),
                TripSummary.getAnonymizedTripSummaries(start, end)
            )
        );
    }

    /**
     * Add to the upload list any dates between now and the last upload date. This will cover a new day once
     * passed midnight and any days missed due to downtime.
     */
    public static void stageUploadDays() {
        Date now = new Date();
        TripHistoryUpload latest = TripHistoryUpload.getLatest();
        if (latest == null) {
            // No data held, add the previous day as the first day to be uploaded.
            Persistence.tripHistoryUploads.create(
                new TripHistoryUpload(
                    getStartOfDay(
                        getDateMinusNumberOfDays(now, 1)
                    )
                ));
        } else {
            Set<LocalDate> betweenDays = DateTimeUtils.getDatesBetween(
                getDatePlusNumberOfDays(latest.uploadDate,1),
                now
            );
            betweenDays.forEach(day -> {
                Persistence.tripHistoryUploads.create(new TripHistoryUpload(getStartOfDay(day)));
            });
        }
    }

    /**
     * Process incomplete upload dates. This will be uploads which are flagged as 'pending'. If the upload date is
     * compiled and uploaded successfully, it is flagged as 'complete'.
     */
    public static void processTripHistory() {
        List<TripHistoryUpload> incompleteUploads = getIncompleteUploads();
        incompleteUploads.forEach(tripHistoryUpload -> {
            if (compileAndUploadTripHistory(tripHistoryUpload.uploadDate)) {
                // Update the status to 'completed' if successfully compiled and uploaded.
                tripHistoryUpload.status = TripHistoryUploadStatus.COMPLETED.getValue();
                Persistence.tripHistoryUploads.replace(tripHistoryUpload.id, tripHistoryUpload);
            }
        });
    }

    /**
     * Obtain anonymize trip data for the given date, write to zip file, upload the zip file to S3 and finally delete
     * the zip file from local disk.
     */
    private static boolean compileAndUploadTripHistory(Date date) {
        Date startOfDay = getStartOfDay(date);
        String zipFileName = getFileName(startOfDay, ZIP_FILE_NAME_SUFFIX);
        File tempZipFile = null;
        try {
            String tempFile = String.join("/", FileUtils.getTempDirectory().getAbsolutePath(), zipFileName);
            FileUtils.writeFileToZip(
                tempFile,
                getFileName(startOfDay, DATA_FILE_NAME_SUFFIX),
                getAnonymizedTripHistory(startOfDay, getEndOfDay(date))
            );
            // Get the complete zip file from the local file system.
            tempZipFile = new File(tempFile);
            S3Utils.putObject(
                CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME,
                CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME + "/" + zipFileName,
                tempZipFile
            );
            return true;
        } catch (S3Exception | IOException e) {
            LOG.error("Failed to process trip data for {}", startOfDay, e);
            return false;
        } finally {
            // Delete the temporary zip file if it was created. This is done here in case the S3 upload fails.
            try {
                if (tempZipFile != null && !IS_TEST) {
                    FileUtils.deleteFile(tempZipFile);
                } else if (tempZipFile != null) {
                    LOG.warn("In test mode, temp zip file {} not deleted. This is expected to be deleted by the calling test",
                        tempZipFile.getAbsolutePath()
                    );
                }
            } catch (IOException e) {
                LOG.error("Failed to delete temp zip file {}", tempZipFile.getAbsolutePath(), e);
            }
        }
    }

    /**
     * Get all dates that have not been uploaded.
     */
    private static List<TripHistoryUpload> getIncompleteUploads() {
        List<TripHistoryUpload> incomplete = new ArrayList<>();
        for (TripHistoryUpload tripHistoryUpload : TripHistoryUpload.getIncompleteUploads()) {
            incomplete.add(tripHistoryUpload);
        }
        return incomplete;
    }

    /**
     * Produce file name.
     */
    public static String getFileName(Date startOfDay, String fileNameSuffix) {
        return String.format(
            "%s-%s",
            getStringFromDate(convertToLocalDate(startOfDay), DEFAULT_DATE_FORMAT_PATTERN),
            fileNameSuffix
        );
    }
}
