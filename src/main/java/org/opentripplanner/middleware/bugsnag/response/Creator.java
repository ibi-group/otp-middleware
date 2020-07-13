package org.opentripplanner.middleware.bugsnag.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * This class is referenced within {@link Organization}. Information relating to this can be found here:
 * https://bugsnagapiv2.docs.apiary.io/#reference/organizations
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Creator {

    /** Organization creator id*/
    public String id;

    /** Organization creator name*/
    public String name;

    /** Organization creator email*/
    public String email;

}
