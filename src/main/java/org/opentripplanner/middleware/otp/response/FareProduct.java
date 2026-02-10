package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FareProduct implements Cloneable {

    // __typename
    public /*FeedScopedId*/ String id;
    public FareMedium medium;
    public String name;
    public RiderCategory riderCategory;
    public Money price;
    List<FareDependency> dependencies = new ArrayList<>();
}
