package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.utils.Coordinates;

import java.util.Date;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.otp.response.Place} containing only parameters
 * that don't contain precise user or location data.
 */
public class AnonymizedPlace {

    public Date arrival;
    public Date departure;
    // If the place is part of a transit leg the coordinates will be as provided by OTP. If the place is part of the
    // first or last leg and is non transit, the coordinates will be randomized.
    public Coordinates coordinates;

    // Transit leg only.
    public String name;
    public String stopCode;
    public String stopId;
    public Integer stopSequence;
}
