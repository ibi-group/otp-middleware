package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Agency {

    /**
     * Agency feed and id.
     */
    public String gtfsId;

    /**
     * Global object ID provided by Relay. This value can be used to re-fetch this object using node query.
     */
    public String id;

    /**
     * Name of the agency.
     */
    public String name;
}
