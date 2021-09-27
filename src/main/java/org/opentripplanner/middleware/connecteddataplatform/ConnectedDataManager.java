package org.opentripplanner.middleware.connecteddataplatform;

import com.google.common.collect.Sets;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.opentripplanner.middleware.models.TripHistoryUpload;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.S3Exception;
import org.opentripplanner.middleware.utils.S3Utils;
import org.opentripplanner.middleware.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
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
import static org.opentripplanner.middleware.utils.DateTimeUtils.getEndOfDay;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getStartOfDay;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getStringFromDate;

public class ConnectedDataManager {

    // If set to true, no files are upload to S3 or deleted from the local disk. This is expected to be carried out by
    // the unit tests instead.
    public static boolean IS_TEST;

    public static final int DATA_RETRIEVAL_LIMIT = 10;
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
            if (first == null ||
                newDate.getTime() == first.uploadDate.getTime() ||
                (newDate.after(first.uploadDate) &&
                    newDate.before(getStartOfDay(new Date())))
            ) {
                // If the new date is the same or after the first ever upload date, add it to the upload list. This acts
                // as a back stop to prevent historic uploads being created indefinitely. Also, make sure the new date
                // is before today because the trip data for it isn't uploaded until after midnight.
                Persistence.tripHistoryUploads.create(new TripHistoryUpload(newDate));
            }
        });
    }

    /**
     * Get all trip requests and trip summaries between the provided dates. Anonymize the data, serialize to JSON and
     * write to temp file.
     */
    private static void streamTripHistoryToFile(String pathAndFileName, Date start, Date end) throws IOException {
        // By not appending here the contents of a potentially existing file will be overwritten.
        FileUtils.writeToFile(pathAndFileName, false, "{");
        streamTripRequestsToFile(pathAndFileName, start, end);
        FileUtils.writeToFile(pathAndFileName, true, ",");
        streamTripSummariesToFile(pathAndFileName, start, end);
        FileUtils.writeToFile(pathAndFileName, true, "}");
    }

    /**
     * Stream trip requests to file. This approach is used to avoid having a large amount of data in memory which could
     * cause an out-of-memory error if there are a lot of trip requests to process.
     */
    private static void streamTripRequestsToFile(String pathAndFileName, Date start, Date end) throws IOException {
        int offset = 0;

        long numberOfTripRequests = Persistence.tripRequests.getCountFiltered(
            Filters.and(
                Filters.gte("dateCreated", start),
                Filters.lte("dateCreated", end)
            )
        );

        FileUtils.writeToFile(pathAndFileName, true, "\"tripRequests\":[");
        while (offset < numberOfTripRequests) {
            // Get limited dataset from db.
            FindIterable<TripRequest> tripRequests = Persistence.tripRequests.getSortedIterableWithOffsetAndLimit(
                Sorts.descending("dateCreated"),
                offset,
                DATA_RETRIEVAL_LIMIT
            );

            // Extract trip requests and convert to anonymized trip request list.
            List<AnonymizedTripRequest> anonymizedTripRequests = tripRequests
                .map(trip -> new AnonymizedTripRequest(
                    trip.batchId,
                    trip.fromPlace,
                    trip.fromPlaceIsPublic,
                    trip.toPlace,
                    trip.toPlaceIsPublic,
                    trip.requestParameters)
                )
                .into(new ArrayList<>());

            // Append content to file
            FileUtils.writeToFile(
                pathAndFileName,
                true,
                correctJson(
                    JsonUtils.toJson(anonymizedTripRequests),
                    offset,
                    anonymizedTripRequests.size(),
                    numberOfTripRequests
                )
            );
            offset += DATA_RETRIEVAL_LIMIT;
        }
        FileUtils.writeToFile(pathAndFileName, true, "]");
    }

    /**
     * Stream trip summaries to file. This approach is used to avoid having a large amount of data in memory which could
     * cause an out-of-memory error if there are a lot of trip summaries to process.
     */
    private static void streamTripSummariesToFile(String pathAndFileName, Date start, Date end) throws IOException {
        int offset = 0;

        long numberOfTripSummaries = Persistence.tripSummaries.getCountFiltered(
            Filters.and(
                Filters.gte("dateCreated", start),
                Filters.lte("dateCreated", end)
            )
        );

        FileUtils.writeToFile(pathAndFileName, true, "\"tripSummaries\":[");
        while (offset < numberOfTripSummaries) {
            // Get limited dataset from db.
            FindIterable<TripSummary> tripSummaries = Persistence.tripSummaries.getSortedIterableWithOffsetAndLimit(
                Sorts.descending("dateCreated"),
                offset,
                DATA_RETRIEVAL_LIMIT
            );

            // Extract trip summaries and convert to anonymized trip summaries list.
            List<AnonymizedTripSummary> anonymizedTripSummaries = tripSummaries
                .map(trip -> new AnonymizedTripSummary(trip.batchId, trip.date, trip.itineraries))
                .into(new ArrayList<>());

            // Append content to file
            FileUtils.writeToFile(
                pathAndFileName,
                true,
                correctJson(
                    JsonUtils.toJson(anonymizedTripSummaries),
                    offset,
                    anonymizedTripSummaries.size(),
                    numberOfTripSummaries
                )
            );
            offset += DATA_RETRIEVAL_LIMIT;
        }
        FileUtils.writeToFile(pathAndFileName, true, "]");
    }

    /**
     * Alter the Json formatting so that the file content will validate correctly once complete.
     */
    private static String correctJson(String fileContent, int offset, int numberOfItems, long totalNumberOfItems) {
        // remove the '[' reference as we only want it in the file once.
        fileContent = fileContent.replaceFirst("\\[", "");
        // remove the final ']' as we only want it in the file once.
        fileContent = fileContent.substring(0, fileContent.length() - 1);
        if (offset + numberOfItems < totalNumberOfItems) {
            // Add a comma to separate this list of items from the next.
            fileContent += ",";
        }
        return fileContent;
    }

    /**
     * Obtain anonymize trip data for the given date, write to zip file, upload the zip file to S3 and finally delete
     * the data and zip files from local disk.
     */
    public static boolean compileAndUploadTripHistory(Date date) {
        Date startOfDay = getStartOfDay(date);
        String zipFileName = getFileName(startOfDay, ZIP_FILE_NAME_SUFFIX);
        String tempZipFile = String.join("/", FileUtils.getTempDirectory().getAbsolutePath(), zipFileName);
        String tempDataFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            getFileName(startOfDay, DATA_FILE_NAME_SUFFIX)
        );
        try {
            streamTripHistoryToFile(tempDataFile, startOfDay, getEndOfDay(date));
            FileUtils.addSingleFileToZip(tempDataFile, tempZipFile);
            S3Utils.putObject(
                CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME,
                CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME + "/" + zipFileName,
                new File(tempZipFile)
            );
            return true;
        } catch (S3Exception | IOException e) {
            LOG.error("Failed to process trip data for {}", startOfDay, e);
            return false;
        } finally {
            // Delete the temporary files. This is done here in case the S3 upload fails.
            try {
                FileUtils.deleteFile(tempDataFile);
                if (!IS_TEST) {
                    FileUtils.deleteFile(tempZipFile);
                } else {
                    LOG.warn("In test mode, temp zip file {} not deleted. This is expected to be deleted by the calling test.",
                        tempZipFile
                    );
                }
            } catch (IOException e) {
                LOG.error("Failed to delete temp files", e);
            }
        }
    }

    /**
     * Get all incomplete trip history uploads.
     */
    public static List<TripHistoryUpload> getIncompleteUploads() {
        FindIterable<TripHistoryUpload> tripHistoryUploads = Persistence.tripHistoryUploads.getFiltered(
            Filters.ne("status", TripHistoryUploadStatus.COMPLETED.getValue())
        );
        return tripHistoryUploads.into(new ArrayList<>());
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
