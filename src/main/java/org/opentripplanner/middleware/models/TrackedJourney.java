package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.triptracker.TrackingLocation;
import org.opentripplanner.middleware.triptracker.TrackingPayload;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TrackedJourney extends Model {

    public String journeyId;

    public String tripId;

    public String endCondition;

    public Date startTime;

    public Date endTime;

    public List<TrackingLocation> locations = new ArrayList<>();

    public TrackedJourney() {
    }

    public TrackedJourney(String journeyId, TrackingPayload trackingPayLoad) {
        this.journeyId = journeyId;
        startTime = new Date();
        locations.add(trackingPayLoad.location);
        tripId = trackingPayLoad.tripId;
    }

    public void update(TrackingPayload trackingPayLoad) {
        this.locations.addAll(trackingPayLoad.locations);
    }

    public void end(String endCondition) {
        this.endTime = new Date();
        this.endCondition = endCondition;
    }

    @Override
    public boolean delete() {
        return Persistence.trackedJourneys.removeById(this.id);
    }

}