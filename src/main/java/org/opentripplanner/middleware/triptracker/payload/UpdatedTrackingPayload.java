package org.opentripplanner.middleware.triptracker.payload;

import org.opentripplanner.middleware.triptracker.TrackingLocation;

import java.util.ArrayList;
import java.util.List;

public class UpdatedTrackingPayload {

    public String journeyId;

    public List<TrackingLocation> locations = new ArrayList<>();

}
