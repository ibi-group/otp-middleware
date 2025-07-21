package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Route {

    /**
     * ID of the route in format FeedId:RouteId.
     */
    public String gtfsId;

    /**
     * Global object ID provided by Relay. This value can be used to re-fetch this object using node query.
     */
    public String id;

    /**
     * Long name of the route.
     */
    public String longName;

    /**
     * Short name of the route, usually a line number, e.g. 550.
     */
    public String shortName;

    /**
     * The raw GTFS route type as a integer. For the list of possible values, see:
     * https://developers.google.com/transit/gtfs/reference/#routestxt and
     * https://developers.google.com/transit/gtfs/reference/extended-route-types
     */
    public Integer type;
}
