package org.opentripplanner.middleware.connecteddataplatform;

import com.google.common.collect.Sets;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.apache.logging.log4j.util.Strings;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.controllers.api.OtpRequestProcessor;
import org.opentripplanner.middleware.models.TripHistoryUpload;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.graphql.QueryVariables;
import org.opentripplanner.middleware.otp.graphql.TransportMode;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;
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
import java.util.Map;
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

    public static final String ANON_TRIP_FILE_NAME = "anon-trip-data";
    public static final String ANON_TRIP_ZIP_FILE_NAME = ANON_TRIP_FILE_NAME + ".zip";
    public static final String ANON_TRIP_JSON_FILE_NAME = ANON_TRIP_FILE_NAME + ".json";
    public static final String ZIP_FILE_EXTENSION = "zip";
    public static final String JSON_FILE_EXTENSION = "json";

    private static final String CONNECTED_DATA_PLATFORM_ENABLED =
        getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_ENABLED", "false");

    private static final int CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES =
        getConfigPropertyAsInt("CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES", 5);

    private static final Logger LOG = LoggerFactory.getLogger(ConnectedDataManager.class);

    public static final String CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME =
        getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME");

    public static final String CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME =
        getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME");

    public static final ReportingInterval CONNECTED_DATA_PLATFORM_REPORTING_INTERVAL = ReportingInterval.valueOf(
        getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_REPORTING_INTERVAL", "hourly").toUpperCase()
    );

    public static final FolderGrouping CONNECTED_DATA_PLATFORM_FOLDER_GROUPING = FolderGrouping.valueOf(
        getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_FOLDER_GROUPING", "none")
            .toUpperCase()
            .replace('-', '_')
    );

    public static final String CONNECTED_DATA_PLATFORM_UPLOAD_BLANK_FILES =
        getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_UPLOAD_BLANK_FILES", "false");

    private static final String DATE_CREATED_FIELD = "dateCreated";
    public static final String BATCH_ID_FIELD = "batchId";

    private ConnectedDataManager() {}

    public static boolean canScheduleUploads() {
        if (!isConnectedDataPlatformEnabled()) {
            LOG.warn("Connected Data Platform is not enabled (CONNECTED_DATA_PLATFORM_ENABLED is set to false).");
            return false;
        }

        if (ReportedEntities.entityMap.isEmpty()) {
            LOG.warn("No entities marked for reporting (CONNECTED_DATA_PLATFORM_REPORTED_ENTITIES has no known entities).");
            return false;
        }

        if (Strings.isBlank(CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME)) {
            LOG.warn("Not scheduling trip history upload (CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME is not set).");
            return false;
        }

        return true;
    }

    public static void scheduleTripHistoryUploadJob() {
        if (canScheduleUploads()) {
            LOG.info("Scheduling trip history upload for every {} minute(s)",
                CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES);
            Scheduler.scheduleJob(
                new TripHistoryUploadJob(),
                0,
                CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES,
                TimeUnit.MINUTES);
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
     * If anonymizing:
     *   4) Define lat/lon for 'from' and 'to' places, scrambling location coordinates for non-public locations.
     *   5) Anonymize trip request with the most modes.
     *   6) Get related trip summaries and anonymize.
     * 7) Write resulting trip requests to file.
     */
    private static int streamTripsToFile(
        String pathAndFileName,
        LocalDateTime periodStart,
        ReportingInterval reportingInterval,
        boolean anonymize
    ) throws IOException {
        Bson dateFilter = getDateFilter(periodStart, reportingInterval);

        // Get distinct batchId values between two dates. Only select trip requests where a batch id has been provided.
        DistinctIterable<String> uniqueBatchIds = Persistence.tripRequests.getDistinctFieldValues(
            BATCH_ID_FIELD,
            Filters.and(
                dateFilter,
                Filters.ne(BATCH_ID_FIELD, OtpRequestProcessor.BATCH_ID_NOT_PROVIDED)
            ),
            String.class
        );

        // Needed to correctly format the JSON content.
        // (If needed for perf and if settings permit: skip writing file if no records.)
        int numberOfUniqueBatchIds = 0;
        for (String batchId : uniqueBatchIds) {
            numberOfUniqueBatchIds++;
        }

        int numTripRequestsWrittenToFile = 0;
        int pos = 0;
        FileUtils.writeToFile(pathAndFileName, false, "[");
        for (String uniqueBatchId : uniqueBatchIds) {
            pos++;

            Bson tripRequestFilter = Filters.and(
                dateFilter,
                Filters.eq(BATCH_ID_FIELD, uniqueBatchId)
            );

            Object tripRequestToWrite = anonymize
                ? getAnonymizedTripRequest(tripRequestFilter, uniqueBatchId)
                : getCombinedTripRequest(tripRequestFilter);
            if (tripRequestToWrite != null) {
                // Append content to file.
                FileUtils.writeToFile(pathAndFileName, true, JsonUtils.toJson(tripRequestToWrite));
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
     * Stream the full Mongo collection to file.
     */
    private static int streamFullCollectionToFile(
        TypedPersistence<?> persistenceType,
        String pathAndFileName
    ) throws IOException {
        return streamCollectionToFile(
            pathAndFileName,
            persistenceType.getAll(),
            persistenceType.getCount()
        );
    }

    /**
     * Stream the records in a given time interval to a file.
     */
    private static int streamPartialCollectionToFile(
        TypedPersistence<?> persistenceType,
        String pathAndFileName,
        LocalDateTime periodStart,
        ReportingInterval reportingInterval
    ) throws IOException {
        Bson dateFilter = getDateFilter(periodStart, reportingInterval);
        return streamCollectionToFile(
            pathAndFileName,
            persistenceType.getFiltered(dateFilter),
            persistenceType.getCountFiltered(dateFilter)
        );
    }

    private static Bson getDateFilter(LocalDateTime periodStart, ReportingInterval reportingInterval) {
        // (Calling getStartOfHour is probably redundant because the starting hour (or day) to be anonymized
        // should already be rounded to a whole hour/day.)
        Date startOfPeriod = DateTimeUtils.getStartOfHour(periodStart);
        Date endOfPeriod = isReportingDaily(reportingInterval)
            ? DateTimeUtils.getEndOfDay(periodStart)
            : DateTimeUtils.getEndOfHour(periodStart);

        // The creation time corresponds to a time a trip request or other entity is made/a tracked journey is started.
        return Filters.and(
            Filters.gte(DATE_CREATED_FIELD, startOfPeriod),
            Filters.lte(DATE_CREATED_FIELD, endOfPeriod)
        );
    }

    /**
     * Stream an entity to file using the provided iterator and count.
     * This approach is used to avoid having a large amount of data in memory which could
     * cause an out-of-memory error if there are many records to process in a Mongo collection.
     * If no records are found, this will produce a file with an empty JSON array "[]".
     */
    private static int streamCollectionToFile(
        String pathAndFileName,
        FindIterable<?> findIterable,
        long count
    ) throws IOException {

        int numTripRequestsWrittenToFile = 0;
        int pos = 0;
        FileUtils.writeToFile(pathAndFileName, false, "[");
        for (var item : findIterable) {
            pos++;
            // Append content to file.
            FileUtils.writeToFile(pathAndFileName, true, JsonUtils.toJson(item));
            if (pos < count) {
                // Add a comma to separate each trip request, except for the last item in the stream
                // prevent JSON formatting errors.
                FileUtils.writeToFile(pathAndFileName, true, ",");
            }
            numTripRequestsWrittenToFile++;
        }
        FileUtils.writeToFile(pathAndFileName, true, "]");
        return numTripRequestsWrittenToFile;
    }

    public static boolean isReportingDaily() {
        return isReportingDaily(CONNECTED_DATA_PLATFORM_REPORTING_INTERVAL);
    }

    public static boolean isReportingDaily(ReportingInterval reportingInterval) {
        return reportingInterval == ReportingInterval.DAILY;
    }

    /**
     * Extract trip request and trip summary data and create an {@link AnonymizedTripRequest}.
     */
    private static AnonymizedTripRequest getAnonymizedTripRequest(Bson filter, String uniqueBatchId) {
        TripRequest tripRequest = getCombinedTripRequest(filter);
        if (tripRequest == null) {
            // This is possible if no trip requests are within the start and end hour.
            return null;
        }
        // Get all trip summaries matching the batch id.
        FindIterable<TripSummary> tripSummaries = Persistence.tripSummaries.getFiltered(
            eq(BATCH_ID_FIELD, uniqueBatchId),
            Sorts.descending(DATE_CREATED_FIELD)
        );
        // Anonymize trip request.
        return new AnonymizedTripRequest(tripRequest, tripSummaries);
    }

    /**
     * Extract a single trip request from multiple trip requests with the same batch id.
     */
    private static TripRequest getCombinedTripRequest(Bson filter) {
        FindIterable<TripRequest> tripRequests = Persistence.tripRequests.getFiltered(
            filter,
            Sorts.descending(DATE_CREATED_FIELD, BATCH_ID_FIELD)
        );
        return getAllModesUsedInBatch(tripRequests);
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
            QueryVariables queryVariables = tripRequest.otp2QueryParams;
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
    public static int compileAndUploadTripHistory(
        LocalDateTime periodStart,
        ReportingInterval reportingInterval,
        Map<String, String> desiredEntitiesToReport
    ) {
        int allRecordsWritten = 0;
        boolean isTest = desiredEntitiesToReport != null;
        Map<String, String> entitiesToReport = desiredEntitiesToReport != null
            ? desiredEntitiesToReport
            : ReportedEntities.entityMap;

        for (var entry : entitiesToReport.entrySet()) {
            String entityName = entry.getKey();
            String reportingMode = entry.getValue();

            // Not null because ReportedEntities only contains entries that correspond to persistenceMap.
            TypedPersistence<?> typedPersistence = ReportedEntities.persistenceMap.get(entityName);
            String filePrefix = getFilePrefix(reportingInterval, periodStart, entityName);
            String tempFileFolder = FileUtils.getTempDirectory().getAbsolutePath();

            String zipFileName = String.join(".", filePrefix, ZIP_FILE_EXTENSION);
            String tempZipFile = String.join(File.separator, tempFileFolder, zipFileName);

            String jsonFileName = String.join(".", filePrefix, JSON_FILE_EXTENSION);
            String tempDataFile = String.join(File.separator, tempFileFolder, jsonFileName);

            try {
                int recordsWritten = Integer.MIN_VALUE;

                if ("TripRequest".equals(entityName)) {
                    // Anonymized trip requests are stored under a special file name.
                    boolean anonymize = isAnonymizedInterval(reportingMode);
                    if (anonymize) {
                        tempDataFile = tempDataFile.replace("TripRequest.json", ANON_TRIP_JSON_FILE_NAME);
                        tempZipFile = tempZipFile.replace("TripRequest.zip", ANON_TRIP_ZIP_FILE_NAME);
                    }

                    // TripRequests must be processed separately because they must be combined, one per batchId.
                    // Note: Anonymized trips include TripRequest and TripSummary in the same entity.
                    recordsWritten = streamTripsToFile(tempDataFile, periodStart, reportingInterval, anonymize);
                } else if (
                    "TripSummary".equals(entityName) &&
                    entitiesToReport.containsKey("TripRequest") &&
                    isAnonymizedInterval(entitiesToReport.get("TripRequest"))
                ) {
                    // Anonymized trip requests already include TripSummary itineraries, so don't create a new file.
                    LOG.info("Skipping TripSummary because they are already included in anonymized trip requests.");
                } else if ("all".equals(reportingMode)) {
                    recordsWritten = streamFullCollectionToFile(typedPersistence, tempDataFile);
                } else if ("interval".equals(reportingMode)) {
                    recordsWritten = streamPartialCollectionToFile(typedPersistence, tempDataFile, periodStart, reportingInterval);
                } else {
                    LOG.error("Report mode '{}' is not implemented for {}.", reportingMode, entityName);
                }

                if (recordsWritten > 0 || "true".equals(CONNECTED_DATA_PLATFORM_UPLOAD_BLANK_FILES)) {
                    // Upload the file if records were written or config setting requires uploading blank files.
                    FileUtils.addSingleFileToZip(tempDataFile, tempZipFile);
                    S3Utils.putObject(
                        CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME,
                        String.format(
                            "%s/%s",
                            getUploadFolderName(
                                CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME,
                                CONNECTED_DATA_PLATFORM_FOLDER_GROUPING,
                                periodStart.toLocalDate()
                            ),
                            zipFileName
                        ),
                        new File(tempZipFile)
                    );
                }
                allRecordsWritten += recordsWritten;
            } catch (Exception e) {
                BugsnagReporter.reportErrorToBugsnag(
                    String.format("Failed to process trip data for (%s)", periodStart),
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

        return allRecordsWritten;
    }

    public static boolean isAnonymizedInterval(String reportingMode) {
        List<String> reportingModes = List.of(reportingMode.split(" "));
        return reportingModes.contains("interval") && reportingModes.contains("anonymized");
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

    public static String getHourlyFileName(LocalDateTime date, String fileNameSuffix) {
        final String DEFAULT_DATE_FORMAT_PATTERN = "yyyy-MM-dd-HH";
        return String.format(
            "%s-%s",
            getStringFromDate(date, DEFAULT_DATE_FORMAT_PATTERN),
            fileNameSuffix
        );
    }

    /**
     * Produce file name without path or extension.
     */
    public static String getFilePrefix(ReportingInterval reportingInterval, LocalDateTime date, String entityName) {
        final String DEFAULT_DATE_FORMAT_PATTERN = isReportingDaily(reportingInterval)
            ? "yyyy-MM-dd"
            : "yyyy-MM-dd-HH";
        return String.format(
            "%s-%s",
            getStringFromDate(date, DEFAULT_DATE_FORMAT_PATTERN),
            entityName
        );
    }

    /**
     * Enable connected data platform if configured to do so.
     */
    private static boolean isConnectedDataPlatformEnabled() {
        return CONNECTED_DATA_PLATFORM_ENABLED.equalsIgnoreCase("true");
    }

    /** Gets the folder name, monday-sunday in YYYY-MM-DD format, based on the prefix and date. */
    public static String getWeeklyMondaySundayFolderName(String prefix, LocalDate date) {
        LocalDate monday = date.minusDays((long)date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        LocalDate sunday = monday.plusDays(6);
        return String.format("%s_%s_%s", prefix, monday.format(DEFAULT_DATE_FORMATTER), sunday.format(DEFAULT_DATE_FORMATTER));
    }

    /** Compute the upload folder name based on folder grouping and date. */
    public static String getUploadFolderName(String baseFolderName, FolderGrouping folderGrouping, LocalDate date) {
        if (folderGrouping == FolderGrouping.WEEKLY_MONDAY_SUNDAY) {
            return getWeeklyMondaySundayFolderName(baseFolderName, date);
        }
        return baseFolderName;
    }
}
