package org.opentripplanner.middleware.otp.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanLabeledLocationInput {
    public String label;
    public PlanLocationInput location;

    // TODO: improve error handling
    public void convertFromFromPlace(String fromPlace) {
        try {
            String[] chunks = fromPlace.split("::");
            String[] coordinates = chunks[1].split(",");
            this.label = chunks[0];
            this.location = new PlanLocationInput();
            this.location.coordinate.latitude = Long.parseLong(coordinates[0]);
            this.location.coordinate.longitude = Long.parseLong(coordinates[1]);
        } catch (Exception e) {
            // TODO: FIX THIS
            System.out.println("Error in parsing fromPlace");
        }
    }

}

