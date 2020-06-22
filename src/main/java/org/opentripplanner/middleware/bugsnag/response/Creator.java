package org.opentripplanner.middleware.bugsnag.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Creator {

    public String id;
    public String name;
    public String email;

}
