package org.opentripplanner.middleware.connecteddataplatform;

import java.util.Date;

/**
 * Anonymous version of {@link org.opentripplanner.middleware.otp.response.Leg} containing only parameters
 * that don't contain precise user or location data.
 */
public class AnonymizedLeg {

    // Parameters for both transit and non transit legs.
    public Double distance;
    public Double duration;
    public Date startTime;
    public Date endTime;
    public String mode;
    public Boolean transitLeg;
    public AnonymizedPlace from;
    public AnonymizedPlace to;

    // Parameters for a transit leg.
    public String agencyId;
    public Boolean interlineWithPreviousLeg;
    public Boolean realTime;
    public String routeId;
    public String routeShortName;
    public String routeLongName;
    public Integer routeType;
    public String tripBlockId;
    public String tripId;

    // Parameters for non transit leg.
    public Boolean hailedCar;
    public Boolean rentedBike;
    public Boolean rentedCar;
    public Boolean rentedVehicle;



}
