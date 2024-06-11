package org.opentripplanner.middleware.triptracker.interactions.busnotifiers;

import org.opentripplanner.middleware.triptracker.TravelerPosition;
import org.opentripplanner.middleware.triptracker.TripStatus;

public interface BusOperatorInteraction {

    void sendNotification(TripStatus tripStatus, TravelerPosition travelerPosition);

    void cancelNotification(TravelerPosition travelerPosition);
}
