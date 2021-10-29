package org.opentripplanner.middleware.connecteddataplatform;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.otp.response.Itinerary} containing only parameters
 * that don't contain precise user or location data.
 */
public class AnonymizedItinerary {

    /**
     * Duration of the trip on this itinerary, in seconds.
     */
    public Long duration = 0L;

    /**
     * Time that the trip departs.
     */
    public Date startTime = null;

    /**
     * Time that the trip arrives.
     */
    public Date endTime = null;

    /**
     * The number of transfers this trip has.
     */
    public Integer transfers = 0;

    /**
     * How much time is spent on transit, in seconds.
     */
    public Long transitTime = 0L;

    /**
     * How much time is spent waiting for transit to arrive, in seconds.
     */
    public Long waitingTime = 0L;

    /**
     * How far the user has to walk, in meters.
     */
    public Double walkDistance = 0.0;

    /**
     * How much time is spent walking, in seconds.
     */
    public Long walkTime = 0L;

    /**
     * Anonymous leg information for this itinerary.
     */
    public List<AnonymizedLeg> legs = new ArrayList<>();
}
