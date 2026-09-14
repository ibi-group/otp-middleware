package org.opentripplanner.middleware.otp.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * PlanModesInput, matching the OTP GraphQL specification
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanModesInput implements Cloneable {
    public List<String> direct;
    public boolean transitOnly;
    public boolean directOnly;
    public PlanTransitModesInput transit;

    @Override
    public PlanModesInput clone() {
        PlanModesInput clone = new PlanModesInput();
        if (direct != null) {
            clone.direct = List.copyOf(direct);
        }
        clone.transitOnly = transitOnly;
        clone.directOnly = directOnly;
        clone.transit = transit.clone();
        return clone;
    }

}

