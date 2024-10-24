package org.opentripplanner.middleware.otp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.opentripplanner.middleware.models.MonitoredTrip;
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
    public String fromPlace;
    public String mobilityProfile;
    public List<OtpGraphQLTransportMode> modes;
    public int numItineraries;
    public OtpGraphQLRoutesAndTrips preferred;
    public String time;
    public String toPlace;
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
        clone.date = date;
        clone.fromPlace = fromPlace;
        clone.mobilityProfile = mobilityProfile;
        if (modes != null) {
            clone.modes = List.copyOf(modes);
        }
        clone.numItineraries = numItineraries;
        if (preferred != null) {
            clone.preferred = preferred.clone();
        }
        clone.time = time;
        clone.toPlace = toPlace;
        if (unpreferred != null) {
            clone.unpreferred = unpreferred.clone();
        }
        clone.walkReluctance = walkReluctance;
        clone.walkSpeed = walkSpeed;
        clone.wheelchair = wheelchair;
        return clone;
    }
}
