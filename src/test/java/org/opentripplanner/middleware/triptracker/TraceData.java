package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.triptracker.instruction.TripInstruction;
import org.opentripplanner.middleware.utils.Coordinates;

import java.time.Instant;

public class TraceData {
    public TripStatus tripStatus = TripStatus.ON_SCHEDULE;
    public Coordinates position;
    public int speed;
    public String expectedInstruction;
    public boolean dismissIntermediateStops;
    public Instant instant;

    public TraceData withPosition(Coordinates position) {
        this.position = position;
        return this;
    }

    public TraceData withPosition(double lat, double lon) {
        return withPosition(new Coordinates(lat, lon));
    }

    public TraceData withExpectedInstruction(String instruction) {
        this.expectedInstruction = instruction;
        return this;
    }

    public TraceData withExpectedInstruction(TripInstruction instruction) {
        return withExpectedInstruction(instruction.build());
    }

    public TraceData withSpeed(int speed) {
        this.speed = speed;
        return this;
    }

    public TraceData withInstant(Instant instant) {
        this.instant = instant;
        return this;
    }

    public TraceData withNullIntermediateStops() {
        this.dismissIntermediateStops = true;
        return this;
    }

    public TraceData withTripStatus(TripStatus tripStatus) {
        this.tripStatus = tripStatus;
        return this;
    }
}
