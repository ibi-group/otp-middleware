package org.opentripplanner.middleware.otp.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanDateTimeInput implements Cloneable{
    public String earliestDeparture;
    public String latestArrival;

    @Override
    public PlanDateTimeInput clone() {
        PlanDateTimeInput clone = new PlanDateTimeInput();
        clone.earliestDeparture = earliestDeparture;
        clone.latestArrival = latestArrival;
        return clone;
    }
}
