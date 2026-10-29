package org.opentripplanner.middleware.otp.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanTransitModesInput implements Cloneable {
    public List<String> access;
    public List<String> egress;
    public List<String> transfer;
    public List<TransportMode> transit;

    @Override
    public PlanTransitModesInput clone() {
        PlanTransitModesInput clone = new PlanTransitModesInput();
        if (access != null) {
            clone.access = List.copyOf(access);
        }
        if (egress != null) {
            clone.egress = List.copyOf(egress);
        }
        if (transfer != null) {
            clone.transfer = List.copyOf(transfer);
        }
        if (transit != null) {
            clone.transit = List.copyOf(transit);
        }
        return clone;
    }
}
