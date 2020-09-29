package org.opentripplanner.middleware.controllers.response;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Generic class for wrapping a paginated list response for a 'get all' HTTP endpoint.
 */
public class ResponseList<T> {
    public List<T> data;
    public int offset;
    public int limit;
    public long total;
    public Date timestamp;

    /**
     * No-arg constructor for de/serialization.
     */
    public ResponseList() { }

    /**
     * Primary constructor for generating a paginated response list.
     * @param collection - Mongo collection from which to construct the list
     * @param filter - filter (query/sort) to apply to find operation (null is OK)
     * @param offset - number of results by which to offset
     * @param limit - number of results by which the response should be limited
     */
    public ResponseList(MongoCollection<T> collection, Bson filter, int offset, int limit){
        FindIterable<T> iterable = filter != null ? collection.find(filter) : collection.find();
        this.data = iterable.skip(offset).limit(limit).into(new ArrayList<>());
        this.offset = offset;
        this.limit = limit;
        this.total = collection.countDocuments();
        this.timestamp = new Date();
    }

    /**
     * Shorthand constructor for generating an unfiltered response list.
     */
    public ResponseList(MongoCollection<T> collection, int offset, int limit){
        this(collection, null, offset, limit);
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
