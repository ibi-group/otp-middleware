package org.opentripplanner.middleware.otp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.otp.graphql.PlanDateTimeInput;
import org.opentripplanner.middleware.otp.graphql.PlanLabeledLocationInput;
import org.opentripplanner.middleware.otp.graphql.PlanModesInput;
import spark.Request;

import java.util.List;

import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromJSON;

/** OTP 'plan' query variables */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OtpGraphQLVariables implements Cloneable {
    public boolean arriveBy;
    public OtpGraphQLRoutesAndTrips banned;
    public Float bikeReluctance;
    public Float carReluctance;
    public String date;
    public PlanDateTimeInput dateTime;
    public PlanLabeledLocationInput origin;
    public String mobilityProfile;
    public PlanModesInput modes;
    public List<OtpGraphQLTransportMode> modesList;
    public int numItineraries;
    public OtpGraphQLRoutesAndTrips preferred;
    public String time;
    public PlanLabeledLocationInput destination;
    public OtpGraphQLRoutesAndTrips unpreferred;
    public Float walkReluctance;
    public Float walkSpeed;
    public boolean wheelchair;

    public static OtpGraphQLVariables fromMonitoredTripRequest(Request request) throws JsonProcessingException {
        return getPOJOFromJSON(request.body(), MonitoredTrip.class).otp2QueryParams;
    }

    @Override
    public OtpGraphQLVariables clone() {
        OtpGraphQLVariables clone = new OtpGraphQLVariables();
        clone.arriveBy = arriveBy;
        clone.banned = banned;
        clone.bikeReluctance = bikeReluctance;
        clone.carReluctance = carReluctance;
        clone.dateTime = dateTime;
        clone.destination = destination;
        clone.mobilityProfile = mobilityProfile;
        if (modesList != null) {
            clone.modesList = List.copyOf(modesList);
        }
        if (modes != null) {
            clone.modes = modes.clone();
        }
        clone.numItineraries = numItineraries;
        if (preferred != null) {
            clone.preferred = preferred.clone();
        }
        clone.time = time;
        clone.origin = origin;
        if (unpreferred != null) {
            clone.unpreferred = unpreferred.clone();
        }
        clone.walkReluctance = walkReluctance;
        clone.walkSpeed = walkSpeed;
        clone.wheelchair = wheelchair;
        return clone;
    }
}
