package org.opentripplanner.middleware.connecteddataplatform;

import com.google.common.collect.Sets;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.opentripplanner.middleware.models.TripHistoryUpload;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.response.Itinerary;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.Coordinates;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.LatLongUtils;
import org.opentripplanner.middleware.utils.S3Exception;
import org.opentripplanner.middleware.utils.S3Utils;
import org.opentripplanner.middleware.utils.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsInt;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.DateTimeUtils.DEFAULT_DATE_FORMAT_PATTERN;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getStartOfDay;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getStringFromDate;

/**
 * Responsible for collating, anonymizing and uploading to AWS s3 trip requests and related trip summaries.
 */
public class ConnectedDataManager {

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
        LOG.info("Scheduling trip history upload for every {} minute(s)",
            CONNECTED_DATA_PLATFORM_TRIP_HISTORY_UPLOAD_JOB_FREQUENCY_IN_MINUTES);
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
        Set<LocalDate> userTripDates = new HashSet<>();
        for (TripRequest request : TripRequest.requestsForUser(userId)) {
            userTripDates.add(getStartOfDay(request.dateCreated));
            // This will delete the trip summaries as well.
            request.delete();
        }
        // Get all dates that have already been earmarked for uploading.
        Set<LocalDate> incompleteUploads = new HashSet<>();
        getIncompleteUploads().forEach(tripHistoryUpload -> incompleteUploads.add(tripHistoryUpload.uploadDate));
        // Save all new dates for uploading.
        Set<LocalDate> newDates = Sets.difference(userTripDates, incompleteUploads);
        TripHistoryUpload first = TripHistoryUpload.getFirst();
        LocalDate startOfToday = LocalDate.now().atStartOfDay().toLocalDate();
        newDates.forEach(newDate -> {
            if (first == null ||
                newDate.isEqual(first.uploadDate) ||
                (newDate.isAfter(first.uploadDate) &&
                    newDate.isBefore(startOfToday))
            ) {
                // If the new date is the same or after the first ever upload date, add it to the upload list. This acts
                // as a back stop to prevent historic uploads being created indefinitely. Also, make sure the new date
                // is before today because the trip data for it isn't uploaded until after midnight.
                Persistence.tripHistoryUploads.create(new TripHistoryUpload(newDate));
            }
        });
    }

    /**
     * Stream trip requests to file. This approach is used to avoid having a large amount of data in memory which could
     * cause an out-of-memory error if there are a lot of trip requests to process.
     *
     * Process:
     *
     * 1) Extract unique batch ids between two dates.
     * 2) For each batch id get matching trip requests.
     * 3) Workout which trip request uses the most modes.
     * 4) Define lat/lon for 'from' and 'to' places.
     * 5) Anonymize trip request with the most modes.
     * 6) Get related trip summaries and anonymize.
     * 7) Write anonymized trip request and related summaries to file.
     */
    private static void streamAnonymousTripsToFile(
        String pathAndFileName,
        LocalDate start,
        LocalDate end,
        boolean isTest
    ) throws IOException {

        // Get distinct batchId values between two dates.
        DistinctIterable<String> uniqueBatchIds = Persistence.tripRequests.getDistinctFieldValues(
            "batchId",
            Filters.and(
                Filters.gte("dateCreated", DateTimeUtils.convertToDate(start)),
                Filters.lte("dateCreated", DateTimeUtils.convertToDate(end))
            ),
            String.class
        );

        int numberOfUniqueBatchIds = 0;
        MongoCursor<String> iterator = uniqueBatchIds.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            numberOfUniqueBatchIds++;
        }

        int pos = 0;
        FileUtils.writeToFile(pathAndFileName, false, "[");
        for (String uniqueBatchId : uniqueBatchIds) {
            pos++;

            // Get trip request batch.
            FindIterable<TripRequest> tripRequests = Persistence.tripRequests.getFiltered(
                Filters.and(
                    Filters.gte("dateCreated", DateTimeUtils.convertToDate(start)),
                    Filters.lte("dateCreated", DateTimeUtils.convertToDate(end)),
                    Filters.eq("batchId", uniqueBatchId)
                ),
                Sorts.descending("dateCreated", "batchId")
            );

            TripRequest tripRequest = getTripRequestWithMaxModes(tripRequests);

            // Get all trip summaries matching the batch id.
            FindIterable<TripSummary> tripSummaries = Persistence.tripSummaries.getFiltered(
                eq("batchId", uniqueBatchId),
                Sorts.descending("dateCreated")
            );

            // Get place coordinates.
            Coordinates fromCoordinates = getPlaceCoordinates(
                tripSummaries,
                true,
                tripRequest.fromPlace,
                isTest
            );
            Coordinates toCoordinates = getPlaceCoordinates(
                tripSummaries,
                false,
                tripRequest.toPlace,
                isTest
            );

            // Anonymize trip request.
            AnonymizedTripRequest anonymizedTripRequest = new AnonymizedTripRequest(
                tripRequest,
                fromCoordinates,
                toCoordinates
            );

            // Extract trip summaries and convert to anonymized trip summaries list.
            List<AnonymizedTripSummary> anonymizedTripSummaries = tripSummaries
                .map(tripSummary -> new AnonymizedTripSummary(tripSummary, fromCoordinates, toCoordinates))
                .into(new ArrayList<>());

            // Append content to file
            FileUtils.writeToFile(
                pathAndFileName,
                true,
                JsonUtils.toJson(new AnonymizedTrip(anonymizedTripRequest, anonymizedTripSummaries))
            );
            if (pos < numberOfUniqueBatchIds) {
                // A comma is not required if processing the last item in the list. This is to prevent JSON formatting
                // errors.
                FileUtils.writeToFile(pathAndFileName, true, ",");
            }
        }
        FileUtils.writeToFile(pathAndFileName, true, "]");
    }

    /**
     * Workout if the first or last leg is public (a transit leg). If the leg is public the coordinates provided by OTP
     * can be used. If not they are randomized. The place value is assumed to be in the format 'location :: lat,lon'.
     */
    private static Coordinates getPlaceCoordinates(
        FindIterable<TripSummary> tripSummaries,
        boolean isFirstLeg,
        String place,
        boolean isTest
    ) {
        boolean placeIsPublic = true;
        for (TripSummary tripSummary : tripSummaries) {
            placeIsPublic = isLegTransit(tripSummary.itineraries, isFirstLeg);
            if (!placeIsPublic) {
                // if a single trip summary is not public the place lat/lon must be randomized.
                break;
            }
        }

        String coords = place.split("::")[1].trim();
        Coordinates coordinates = new Coordinates(
            Double.parseDouble(coords.split(",")[0]),
            Double.parseDouble(coords.split(",")[1])
        );
        return placeIsPublic ? coordinates : LatLongUtils.getRandomizedCoordinates(coordinates, isTest);
    }

    /**
     * Extract the trip request that uses the most modes.
     */
    private static TripRequest getTripRequestWithMaxModes(FindIterable<TripRequest> tripRequests) {
        TripRequest requestMaxModes = null;
        for (TripRequest tripRequest : tripRequests) {
            if (requestMaxModes == null) {
                requestMaxModes = tripRequest;
            } else if (
                tripRequest.requestParameters.get("mode").length() >
                    requestMaxModes.requestParameters.get("mode").length()
            ) {
                requestMaxModes = tripRequest;
            }
        }
        return requestMaxModes;
    }

    /**
     * Using the legs from the first itinerary, define whether or not the first or last leg is a transit leg. It is
     * assumed that the first and last legs are the same for all itineraries. If the leg is transit, return true else
     * false. E.g. If the first leg is non transit, the related 'fromPlace' lat/lon is randomized because it is not
     * a public location.
     */
    private static boolean isLegTransit(List<Itinerary> itineraries, boolean isFirstLeg) {
        if (itineraries != null &&
            !itineraries.isEmpty() &&
            itineraries.get(0).legs != null &&
            !itineraries.get(0).legs.isEmpty()
        ) {
            return (isFirstLeg)
                ? itineraries.get(0).legs.get(0).transitLeg
                : itineraries.get(0).legs.get(itineraries.get(0).legs.size() - 1).transitLeg;
        }
        return false;
    }

    /**
     * Obtain anonymize trip data for the given date, write to zip file, upload the zip file to S3 and finally delete
     * the data and zip files from local disk.
     */
    public static boolean compileAndUploadTripHistory(LocalDate date, boolean isTest) {
        LocalDate startOfDay = date.atStartOfDay().toLocalDate();
        LocalDate endOfDay = date.atTime(LocalTime.MAX).toLocalDate();
        String zipFileName = getFileName(startOfDay, ZIP_FILE_NAME_SUFFIX);
        String tempZipFile = String.join("/", FileUtils.getTempDirectory().getAbsolutePath(), zipFileName);
        String tempDataFile = String.join(
            "/",
            FileUtils.getTempDirectory().getAbsolutePath(),
            getFileName(startOfDay, DATA_FILE_NAME_SUFFIX)
        );
        try {
            streamAnonymousTripsToFile(tempDataFile, startOfDay, endOfDay, isTest);
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
    public static String getFileName(LocalDate startOfDay, String fileNameSuffix) {
        return String.format(
            "%s-%s",
            getStringFromDate(startOfDay, DEFAULT_DATE_FORMAT_PATTERN),
            fileNameSuffix
        );
    }
}
