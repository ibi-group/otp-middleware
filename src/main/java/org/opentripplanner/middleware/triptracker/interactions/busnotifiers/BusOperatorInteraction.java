package org.opentripplanner.middleware.triptracker.interactions.busnotifiers;

import org.opentripplanner.middleware.otp.response.Leg;
import org.opentripplanner.middleware.triptracker.TravelerPosition;
import org.opentripplanner.middleware.triptracker.TripStatus;

public interface BusOperatorInteraction {

    void sendNotification(TravelerPosition travelerPosition, Leg busLeg);

    void cancelNotification(TravelerPosition travelerPosition, Leg busLeg);
}
