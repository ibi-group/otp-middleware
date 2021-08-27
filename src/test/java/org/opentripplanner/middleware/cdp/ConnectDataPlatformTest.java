package org.opentripplanner.middleware.cdp;

import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.TripHistoryUpload;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getDateMinusNumberOfDays;
import static org.opentripplanner.middleware.utils.DateTimeUtils.getStartOfDay;

public class ConnectDataPlatformTest  extends OtpMiddlewareTestEnvironment {

    /**
     * Make sure that the first upload is created and contains the correct upload date.
     */
    @Test
    public void canStageFirstUpload() {
        ConnectedDataManager.stageUploadDays();
        TripHistoryUpload first = TripHistoryUpload.getFirst();
        Date startOfDay = getStartOfDay(getDateMinusNumberOfDays(new Date(), 1));
        assertNotNull(first);
        assertEquals(startOfDay.getTime(), first.uploadDate.getTime());
        Persistence.tripHistoryUploads.removeById(first.id);
    }
}
