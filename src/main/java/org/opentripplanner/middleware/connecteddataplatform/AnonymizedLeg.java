package org.opentripplanner.middleware.connecteddataplatform;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.otp.response.Leg} containing only parameters
 * flagged as anonymous.
 */
public class AnonymizedLeg {

    public Boolean interlineWithPreviousLeg;
    public String mode;
    public Boolean realTime;
    public String routeId;
    public String routeShortName;
    public String routeLongName;
    public Integer routeType;
    public Boolean transitLeg;
    public String tripId;
    public AnonymizedPlace from;
    public AnonymizedPlace to;
}
