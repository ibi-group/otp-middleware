package org.opentripplanner.middleware.connecteddataplatform;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;
import org.opentripplanner.middleware.models.IntervalUpload;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.isReportingDaily;

/**
 * This job is responsible for keeping the uploads held on S3 up-to-date by defining the hours/days which should be
 * uploaded and triggering the upload process.
 */
public abstract class IntervalUploadJob<T extends IntervalUpload> implements Runnable {

    private static final int HISTORIC_UPLOAD_HOURS_BACK_STOP = 24;
    public static final String STATUS_FIELD_NAME = "status";

    protected final ReportingInterval reportingInterval;
    private final Logger logger;
    private final TypedPersistence<T> persistence;

    protected IntervalUploadJob(Logger logger, ReportingInterval reportingInterval, TypedPersistence<T> persistence) {
        this.logger = logger;
        this.reportingInterval = reportingInterval;
        this.persistence = persistence;
    }

    protected abstract void processInterval(T intervalUpload);

    protected abstract void createUpload(LocalDateTime time);

    public void run() {
        logger.info("{} started", this.getClass().getSimpleName());
        if (isReportingDaily(reportingInterval)) {
            stageUploadDays();
        } else {
            stageUploadHours();
        }
        runInnerLogic();
    }

    public void runInnerLogic() {
        getIncompleteUploads().forEach(this::processInterval);
    }

    /**
     * Add to the upload list any hours between the previous whole hour and the last created (pending or
     * completed) upload. This will cover any hours missed due to downtime and add the latest upload hour
     * if not already accounted for.
     */
    public void stageUploadHours() {
        stageUploadTimes(DateTimeUtils.getPreviousWholeHourFrom(LocalDateTime.now()), ChronoUnit.HOURS);
    }

    /**
     * Add to the upload list any days between the previous day and the last created (pending or
     * completed) upload. This will cover any days missed due to downtime and add the latest upload day
     * if not already accounted for.
     */
    public void stageUploadDays() {
        stageUploadTimes(DateTimeUtils.getPreviousDayFrom(LocalDateTime.now()), ChronoUnit.DAYS);
    }

    /**
     * Add to the upload list any hours/days between the previous whole hour/day and the last created
     * (pending or completed) upload. This will cover any hours/days missed due to downtime,
     * up to HISTORIC_UPLOAD_HOURS_BACK_STOP hours, and add the latest upload hour/day if not already accounted for.
     */
    private void stageUploadTimes(LocalDateTime previousTime, ChronoUnit chronoUnit) {
        IntervalUpload lastCreated = getLastUploadCreated();
        if (lastCreated == null) {
            // Stage first ever upload hour/day (will use 'hour' throughout whether referring to hours or days).
            createUpload(previousTime);
            logger.debug("Staging first ever upload hour: {}.", previousTime);
            return;
        }
        // Stage all time between the last time uploaded and an hour/day ago.
        List<LocalDateTime> intermediateTimes = DateTimeUtils.getTimeUnitsBetween(
            lastCreated.uploadHour,
            previousTime,
            chronoUnit
        );
        intermediateTimes.forEach(uploadHour -> {
            if (uploadHour.isAfter(getHistoricDateTimeBackStop())) {
                logger.debug(
                    "Staging hour: {} that is between last created: {} and the previous whole hour: {}",
                    lastCreated,
                    previousTime,
                    uploadHour
                );
                createUpload(uploadHour);
            }
        });
        if (!lastCreated.uploadHour.isEqual(previousTime)) {
            // Last created is not the latest upload hour, so stage an hour ago.
            createUpload(previousTime);
            logger.debug("Last created {} is older than the latest {}, so staging.", lastCreated, previousTime);
        }
    }

    /**
     * This is the absolute historic date/time which trip history will be uploaded. This assumes that the service will
     * not be offline longer than this period, but if it is, it will prevent potentially a lot of data being uploaded on
     * start-up which will impact performance.
     */
    private static LocalDateTime getHistoricDateTimeBackStop() {
        return LocalDateTime.now().minusHours(HISTORIC_UPLOAD_HOURS_BACK_STOP);
    }

    /**
     * Produce file name without path or extension, depending on whether reporting is hourly or daily.
     */
    public String getFilePrefix(LocalDateTime date, String entityName) {
        return ConnectedDataManager.getFilePrefix(reportingInterval, date, entityName);
    }

    /**
     * Get all incomplete uploads.
     */
    public List<T> getIncompleteUploads() {
        FindIterable<T> incompleteUploads = persistence.getFiltered(
            Filters.ne(STATUS_FIELD_NAME, TripHistoryUploadStatus.COMPLETED.getValue())
        );
        return incompleteUploads.into(new ArrayList<>());
    }

    /**
     * Get the last created trip history upload regardless of status.
     */
    @BsonIgnore
    public T getLastUploadCreated() {
        return getOneOrdered(Sorts.descending("dateCreated"));
    }

    /**
     * Get the first created trip history upload regardless of status.
     */
    @BsonIgnore
    public T getFirstUpload() {
        return getOneOrdered(Sorts.ascending("dateCreated"));
    }

    /**
     * Get one upload based on the sort order.
     */
    private T getOneOrdered(Bson sortBy) {
        return persistence.getOneFiltered(
            Filters.or(
                Filters.eq(STATUS_FIELD_NAME, TripHistoryUploadStatus.COMPLETED.getValue()),
                Filters.eq(STATUS_FIELD_NAME, TripHistoryUploadStatus.PENDING.getValue())
            ),
            sortBy
        );
    }
}
