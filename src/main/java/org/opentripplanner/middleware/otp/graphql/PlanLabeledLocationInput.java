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
            String[] coordinates = chunks[chunks.length == 1 ? 0 : 1].split(",");
            if (chunks.length == 2) {
                this.label = chunks[0];
            } else {
                coordinates = chunks[0].split(",");
            }

            this.location = new PlanLocationInput();
            this.location.coordinate = new PlanCoordinateInput();
            this.location.coordinate.latitude = Double.parseDouble(coordinates[0]);
            this.location.coordinate.longitude = Double.parseDouble(coordinates[1]);
        } catch (Exception e) {
            System.out.println("Error in parsing fromPlace");
        }
    }

}

