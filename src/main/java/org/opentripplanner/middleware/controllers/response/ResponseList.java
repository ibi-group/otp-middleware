package org.opentripplanner.middleware.controllers.response;

import com.mongodb.client.MongoCollection;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Generic class for wrapping a paginated list response for a 'get all' HTTP endpoint.
 */
public class ResponseList<T> {
    /** Data elements requested */
    public List<T> data;
    /** number of results by which to offset */
    public int offset;
    /** number of results by which the response should be limited */
    public int limit;
    /** total results found in query */
    public long total;
    /** time that response was constructed */
    public Date timestamp;

    /**
     * No-arg constructor for de/serialization.
     */
    public ResponseList() { }

    /**
     * Primary constructor for generating a paginated response list.
     * @param collection - Mongo collection from which to construct the list
     * @param filter - filter (query) to apply to find operation
     * @param offset - number of results by which to offset
     * @param limit - number of results by which the response should be limited
     */
    public ResponseList(MongoCollection<T> collection, Bson filter, int offset, int limit){
        this(
            collection.find(filter).skip(offset).limit(limit).into(new ArrayList<>()),
            offset,
            limit,
            collection.countDocuments(filter)
        );
    }

    /**
     * Shorthand constructor for generating an unfiltered response list.
     */
    public ResponseList(MongoCollection<T> collection, int offset, int limit){
        this(
            collection.find().skip(offset).limit(limit).into(new ArrayList<>()),
            offset,
            limit,
            collection.countDocuments()
        );
    }

    /**
     * Alternate constructor to generate paginated response when the data cannot be derived directly from a MongoDB
     * collection (e.g., with {@link org.opentripplanner.middleware.bugsnag.EventSummary}).
     */
    public ResponseList(List<T> data, int offset, int limit, long total){
        this.data = data;
        this.offset = offset;
        this.limit = limit;
        this.total = total;
        this.timestamp = new Date();
    }
}
