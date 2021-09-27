package org.opentripplanner.middleware.connecteddataplatform;

import java.util.Date;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.otp.response.Place} containing only parameters
 * flagged as anonymous.
 */
public class AnonymizedPlace {

    public Date arrival;
    public Date departure;
    public Double lon;
    public Double lat;
    public String name;
    public String stopCode;
    public String stopId;
    public Integer stopSequence;
}
