package org.opentripplanner.middleware.triptracker.interactions;

import org.opentripplanner.middleware.models.OtpUser;

public interface Interaction {
    void triggerAction(SegmentAction segmentAction, OtpUser otpUser);
}
