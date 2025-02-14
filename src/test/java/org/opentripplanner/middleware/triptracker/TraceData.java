package org.opentripplanner.middleware.triptracker;

import org.opentripplanner.middleware.triptracker.instruction.TripInstruction;
import org.opentripplanner.middleware.utils.Coordinates;

import java.time.Instant;

class TraceData {
    TripStatus tripStatus = TripStatus.ON_SCHEDULE;
    Coordinates position;
    int speed;
    String expectedInstruction;
    boolean isStartOfTrip;
    boolean dismissIntermediateStops;
    Instant instant;
    String message;

    public TraceData(Coordinates position, String expectedInstruction, boolean isStartOfTrip, String message) {
        this.position = position;
        this.expectedInstruction = expectedInstruction;
        this.isStartOfTrip = isStartOfTrip;
        this.message = message;
    }

    public TraceData(Coordinates position, TripInstruction expectedInstruction, boolean isStartOfTrip, String message) {
        this(position, expectedInstruction.build(), isStartOfTrip, message);
    }

    public TraceData(Coordinates position, String expectedInstruction, String message) {
        this(position, expectedInstruction, false, message);
    }

    public TraceData(Coordinates position, String expectedInstruction, Instant instant, String message) {
        this(position, expectedInstruction, false, message);
        this.instant = instant;
    }

    public TraceData(Coordinates position, String expectedInstruction, String message, boolean dismissIntermediateStops) {
        this(position, expectedInstruction, false, message);
        this.dismissIntermediateStops = dismissIntermediateStops;
    }

    public TraceData(Coordinates position, int speed, String expectedInstruction, String message) {
        this(position, expectedInstruction, false, message);
        this.speed = speed;
    }

    public TraceData(TripStatus tripStatus, Coordinates position, String expectedInstruction, boolean isStartOfTrip, String message) {
        this.tripStatus = tripStatus;
        this.position = position;
        this.expectedInstruction = expectedInstruction;
        this.isStartOfTrip = isStartOfTrip;
        this.message = message;
    }

    public TraceData(TripStatus tripStatus, Coordinates position, TripInstruction expectedInstruction, boolean isStartOfTrip, String message) {
        this(tripStatus, position, expectedInstruction.build(), isStartOfTrip, message);
    }

    public TraceData(TripStatus tripStatus, Coordinates position, String expectedInstruction, String message) {
        this(tripStatus, position, expectedInstruction, false, message);
    }
}
