package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Bugsnag event request. The class is used for both Mongo storage and JSON deserialization.
 * Information relating to this can be found here:
 * https://bugsnagapiv2.docs.apiary.io/#reference/organizations/collaborators/create-an-event-data-request
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BugsnagEventRequest extends Model {

    /** Event data request id which is unique to this request */
    @JsonProperty("id")
    public String eventDataRequestId;

    /** The status of the event data request e.g. PREPARING, COMPLETED etc */
    public String status;

    /** The total number of events that are expected to be returned */
    public int total;

    /** URL for downloading the report of the requested event data */
    public String url;

    /** This no-arg constructor exists to make MongoDB happy. */
    public BugsnagEventRequest() {
    }

}
