package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.utils.Coordinates;

import java.util.Date;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.otp.response.Place} containing only parameters
 * flagged as anonymous.
 */
public class AnonymizedPlace {

    public Date arrival;
    public Date departure;
    public Coordinates coordinates;

    // Transit leg only.
    public String name;
    public String stopCode;
    public String stopId;
    public Integer stopSequence;
}
