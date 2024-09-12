package org.opentripplanner.middleware.connecteddataplatform;

import com.google.common.collect.Sets;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.apache.logging.log4j.util.Strings;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.controllers.api.OtpRequestProcessor;
import org.opentripplanner.middleware.models.TripHistoryUpload;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.graphql.QueryVariables;
import org.opentripplanner.middleware.otp.graphql.TransportMode;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.S3Utils;
import org.opentripplanner.middleware.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMATTER;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getStartOfCurrentHour;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getStartOfHour;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getStringFromDate;

/**
 * Responsible for collating, anonymizing and uploading to AWS s3 trip requests and related itineraries.
 */
public class ConnectedDataManager {

    public static final String FILE_NAME_SUFFIX = "anon-trip-data";
    public static final String ZIP_FILE_NAME_SUFFIX = FILE_NAME_SUFFIX + ".zip";
    public static final String DATA_FILE_NAME_SUFFIX = FILE_NAME_SUFFIX + ".json";

    private static final String CONNECTED_DATA_PLATFORM_ENABLED =
        getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_ENABLED", "false");

    private static final int CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES =
        getConfigPropertyAsInt("CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES", 5);

    private static final Logger LOG = LoggerFactory.getLogger(ConnectedDataManager.class);

    public static final String CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME =
        getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME");

    public static final String CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME =
        getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME");

    public static final String CONNECTED_DATA_PLATFORM_AGGREGATION_FREQUENCY =
        getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_AGGREGATION_FREQUENCY", "hourly");

    private ConnectedDataManager() {}

    public static void scheduleTripHistoryUploadJob() {
        if (!isConnectedDataPlatformEnabled()) {
            LOG.warn("Connected Data Platform is not enabled (CONNECTED_DATA_PLATFORM_ENABLED is set to false).");
            return;
        }
        if (shouldProcessTripHistory(CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME)) {
            LOG.info("Scheduling trip history upload for every {} minute(s)",
                CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES);
            Scheduler.scheduleJob(
                new TripHistoryUploadJob(),
                0,
                CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES,
                TimeUnit.MINUTES);
        } else {
            LOG.warn("Not scheduling trip history upload (CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME is not set).");
        }
    }

    /**
     * Remove a user's trip requests and trip summaries from the database. Record the user's trip hourly windows so that
     * all data previously uploaded to s3 can be recompiled and re-uploaded to replace what was previously held.
     */
    public static void removeUsersTripHistory(String userId) {
        Set<LocalDateTime> userTripHourlyWindows = new HashSet<>();
        for (TripRequest request : TripRequest.requestsForUser(userId)) {
            userTripHourlyWindows.add(getStartOfHour(request.dateCreated));
            // This will delete the trip summaries as well.
            request.delete();
        }
        // Get all hourly windows that have already been earmarked for uploading.
        Set<LocalDateTime> incompleteUploadHours = new HashSet<>();
        getIncompleteUploads().forEach(tripHistoryUpload -> incompleteUploadHours.add(tripHistoryUpload.uploadHour));
        // Save all new hourly windows for uploading.
        Set<LocalDateTime> newHourlyWindows = Sets.difference(userTripHourlyWindows, incompleteUploadHours);
        TripHistoryUpload first = TripHistoryUpload.getFirst();
        LocalDateTime startOfCurrentHour = getStartOfCurrentHour();
        newHourlyWindows.forEach(newHourlyWindow -> {
            if (first == null ||
                newHourlyWindow.isEqual(first.uploadHour) ||
                (newHourlyWindow.isAfter(first.uploadHour) && newHourlyWindow.isBefore(startOfCurrentHour))
            ) {
                // If the new hourly window is the same or after the first ever upload hour, add it to the upload list.
                // This acts as a backstop to prevent historic uploads being created indefinitely. Also, make sure the
                // new hourly window is before the current hour because the trip data for it hasn't been uploaded yet!
                Persistence.tripHistoryUploads.create(new TripHistoryUpload(newHourlyWindow));
            }
        });
    }

    /**
     * Stream trip requests to file. This approach is used to avoid having a large amount of data in memory which could
     * cause an out-of-memory error if there are a lot of trip requests to process.
     *
     * Process:
     *
     * 1) Extract unique batch ids for a given hour.
     * 2) For each batch id get matching trip requests.
     * 3) Workout which trip request uses the most modes.
     * 4) Define lat/lon for 'from' and 'to' places, scrambling location coordinates for non-public locations.
     * 5) Anonymize trip request with the most modes.
     * 6) Get related trip summaries and anonymize.
     * 7) Write anonymous trip requests to file.
     */
    private static int streamAnonymousTripsToFile(
        String pathAndFileName,
        LocalDateTime hourToBeAnonymized
    ) throws IOException {
        Date startOfHour = DateTimeUtils.getStartOfHour(hourToBeAnonymized);
        Date endOfHour = DateTimeUtils.getEndOfHour(hourToBeAnonymized);
        final String dateCreatedFieldName = "dateCreated";
        final String batchIdFieldName = "batchId";

        // Get distinct batchId values between two dates. Only select trip requests where a batch id has been provided.
        DistinctIterable<String> uniqueBatchIds = Persistence.tripRequests.getDistinctFieldValues(
            batchIdFieldName,
            Filters.and(
                Filters.gte(dateCreatedFieldName, startOfHour),
                Filters.lte(dateCreatedFieldName, endOfHour),
                Filters.ne(batchIdFieldName, OtpRequestProcessor.BATCH_ID_NOT_PROVIDED)
            ),
            String.class
        );

        // Needed to correctly format the JSON content.
        int numberOfUniqueBatchIds = 0;
        for (String batchId : uniqueBatchIds) {
            numberOfUniqueBatchIds++;
        }

        int numTripRequestsWrittenToFile = 0;
        if (numberOfUniqueBatchIds == 0) {
            // No unique batch ids (and therefore no trip requests) to process.
            return numTripRequestsWrittenToFile;
        }

        int pos = 0;
        FileUtils.writeToFile(pathAndFileName, false, "[");
        for (String uniqueBatchId : uniqueBatchIds) {
            pos++;
            // Anonymize trip request.
            AnonymizedTripRequest anonymizedTripRequest = getAnonymizedTripRequest(uniqueBatchId, startOfHour, endOfHour);
            if (anonymizedTripRequest != null) {
                // Append content to file.
                FileUtils.writeToFile(pathAndFileName, true, JsonUtils.toJson(anonymizedTripRequest));
                if (pos < numberOfUniqueBatchIds) {
                    // Add a comma to separate each trip request. This is not required for the last trip request to
                    // prevent JSON formatting errors.
                    FileUtils.writeToFile(pathAndFileName, true, ",");
                }
                numTripRequestsWrittenToFile++;
            }
        }
        FileUtils.writeToFile(pathAndFileName, true, "]");
        return numTripRequestsWrittenToFile;
    }

    /**
     * Extract trip request and trip summary data and create an {@link AnonymizedTripRequest}.
     */
    private static AnonymizedTripRequest getAnonymizedTripRequest(
        String uniqueBatchId,
        Date startOfHour,
        Date endOfHour
    ) {
        final String dateCreatedFieldName = "dateCreated";
        final String batchIdFieldName = "batchId";
        // Get trip request batch.
        FindIterable<TripRequest> tripRequests = Persistence.tripRequests.getFiltered(
            Filters.and(
                Filters.gte(dateCreatedFieldName, startOfHour),
                Filters.lte(dateCreatedFieldName, endOfHour),
                Filters.eq(batchIdFieldName, uniqueBatchId)
            ),
            Sorts.descending(dateCreatedFieldName, batchIdFieldName)
        );
        TripRequest tripRequest = getAllModesUsedInBatch(tripRequests);
        if (tripRequest == null) {
            // This is possible if no trip requests are within the start and end hour.
            return null;
        }
        // Get all trip summaries matching the batch id.
        FindIterable<TripSummary> tripSummaries = Persistence.tripSummaries.getFiltered(
            eq(batchIdFieldName, uniqueBatchId),
            Sorts.descending(dateCreatedFieldName)
        );
        // Anonymize trip request.
        return new AnonymizedTripRequest(tripRequest, tripSummaries);
    }

    /**
     * A single trip query results in many calls from the UI to OTP covering different combinations of modes. The trip
     * request to be included in the anonymous trip data must include all modes used across all trip requests within a
     * batch.
     */
    private static TripRequest getAllModesUsedInBatch(FindIterable<TripRequest> tripRequests) {
        TripRequest request = null;
        Set<String> allUniqueModes = new HashSet<>();
        for (TripRequest tripRequest : tripRequests) {
            if (request == null) {
                // Select the first request. All subsequent requests will be the same apart from the mode (which is
                // overwritten at the end of this method).
                request = tripRequest;
            }
            QueryVariables queryVariables = request.otp2QueryParams;
            List<TransportMode> modes = queryVariables != null ? queryVariables.modes : null;
            if (modes != null && !modes.isEmpty()) {
                allUniqueModes.addAll(AnonymizedTripRequest.getModes(modes));
            }
        }
        if (request != null && request.otp2QueryParams != null) {
            // Replace the mode parameter in the first request with all unique modes from across the batch.
            request.otp2QueryParams.modes = allUniqueModes.stream()
                .map(TransportMode::new)
                .collect(Collectors.toList());
        }
        return request;
    }

    /**
     * Anonymize trip data, write to zip file, upload the zip file to S3 and finally delete the data and zip files from
     * local disk.
     */
    public static int compileAndUploadTripHistory(LocalDateTime hourToBeAnonymized, boolean isTest) {
        String zipFileName = getFileName(hourToBeAnonymized, ZIP_FILE_NAME_SUFFIX);
        String tempZipFile = String.join("/", FileUtils.getTempDirectory().getAbsolutePath(), zipFileName);
        String tempDataFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            getFileName(hourToBeAnonymized, DATA_FILE_NAME_SUFFIX)
        );
        try {
            int numTripRequestsWrittenToFile = streamAnonymousTripsToFile(tempDataFile, hourToBeAnonymized);
            if (numTripRequestsWrittenToFile > 0) {
                // No point doing these tasks if no trip requests were written to file.
                FileUtils.addSingleFileToZip(tempDataFile, tempZipFile);
                S3Utils.putObject(
                    CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME,
                    CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME + "/" + zipFileName,
                    new File(tempZipFile)
                );
            }
            return numTripRequestsWrittenToFile;
        } catch (Exception e) {
            BugsnagReporter.reportErrorToBugsnag(
                String.format("Failed to process trip data for (%s)", hourToBeAnonymized),
                e
            );
            return Integer.MIN_VALUE;
        } finally {
            // Delete the temporary files. This is done here in case the S3 upload fails.
            try {
                LOG.error("Deleting CDP zip file {} as an error occurred while processing the data it was supposed to contain.", tempZipFile);
                FileUtils.deleteFile(tempDataFile);
                if (!isTest) {
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
    public static String getFileName(LocalDateTime date, String fileNameSuffix) {
        final String DEFAULT_DATE_FORMAT_PATTERN = "yyyy-MM-dd-HH";
        return String.format(
            "%s-%s",
            getStringFromDate(date, DEFAULT_DATE_FORMAT_PATTERN),
            fileNameSuffix
        );
    }

    /**
     * Determines whether trip history should be processed based on the configured bucket name.
     * @return true if trip history should be processed, false otherwise.
     */
    public static boolean shouldProcessTripHistory(String configBucketName) {
        return !Strings.isBlank(configBucketName);
    }

    /**
     * Enable connected data platform if configured to do so.
     */
    private static boolean isConnectedDataPlatformEnabled() {
        return CONNECTED_DATA_PLATFORM_ENABLED.equalsIgnoreCase("true");
    }

    /** Gets the folder name, monday-sunday in YYYY-MM-DD format, based on the prefix and date. */
    public static String getWeeklyMondaySundayFolderName(String prefix, LocalDate date) {
        LocalDate monday = date.minusDays(date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        LocalDate sunday = monday.plusDays(6);
        return String.format("%s_%s_%s", prefix, monday.format(DEFAULT_DATE_FORMATTER), sunday.format(DEFAULT_DATE_FORMATTER));
    }
}
