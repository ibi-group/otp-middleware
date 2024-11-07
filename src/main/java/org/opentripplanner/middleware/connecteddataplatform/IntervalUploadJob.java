package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.models.IntervalUpload;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * This job is responsible for keeping the uploads held on S3 up-to-date by defining the hours/days which should be
 * uploaded and triggering the upload process.
 */
public abstract class IntervalUploadJob implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(IntervalUploadJob.class);
    private static final int HISTORIC_UPLOAD_HOURS_BACK_STOP = 24;

    protected boolean isDaily;

    protected IntervalUploadJob(boolean isDaily) {
        this.isDaily = isDaily;
    }

    protected abstract void runInnerLogic();

    protected abstract void createUpload(LocalDateTime time);

    protected abstract IntervalUpload getLastUploadCreated();

    public void run() {
        if (isDaily) {
            stageUploadDays();
        } else {
            stageUploadHours();
        }
        runInnerLogic();
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
            LOG.debug("Staging first ever upload hour: {}.", previousTime);
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
                LOG.debug(
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
            LOG.debug("Last created {} is older than the latest {}, so staging.", lastCreated, previousTime);
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
}
