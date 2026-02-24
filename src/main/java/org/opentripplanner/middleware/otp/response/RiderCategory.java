package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RiderCategory {

    public /*FeedScopedId*/ String id;
    public String name;
    @JsonIgnore
    @BsonIgnore
    public boolean isDefault;
}
