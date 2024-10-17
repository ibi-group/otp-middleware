package org.opentripplanner.middleware.triptracker.interactions;

import org.opentripplanner.middleware.models.OtpUser;

/** Test interaction class to check that an interaction was triggered. */
public class TrivialTripAction implements Interaction {

    private static String lastSegmentId;

    public static String getLastSegmentId() {
        return lastSegmentId;
    }

    public static void setLastSegmentId(String lastSegmentId) {
        TrivialTripAction.lastSegmentId = lastSegmentId;
    }

    @Override
    public void triggerAction(SegmentAction segmentAction, OtpUser otpUser) {
        setLastSegmentId(segmentAction.id);
    }
}
