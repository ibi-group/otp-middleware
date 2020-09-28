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
    public int page;
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
     * @param page - page number to start from
     * @param limit - number of results by which the response should be limited
     */
    public ResponseList(MongoCollection<T> collection, Bson filter, int page, int limit){
        FindIterable<T> iterable = filter != null ? collection.find(filter) : collection.find();
        this.data = iterable.skip(page * limit).limit(limit).into(new ArrayList<>());
        this.page = page;
        this.limit = limit;
        this.total = collection.countDocuments();
        this.timestamp = new Date();
    }

    /**
     * Shorthand constructor for generating an unfiltered response list.
     */
    public ResponseList(MongoCollection<T> collection, int page, int limit){
        this(collection, null, page, limit);
    }

    /**
     * Alternate constructor to generate paginated response when the data cannot be derived directly from a MongoDB
     * collection (e.g., with {@link org.opentripplanner.middleware.bugsnag.EventSummary}).
     */
    public ResponseList(List<T> data, int page, int limit, long total){
        this.data = data;
        this.page = page;
        this.limit = limit;
        this.total = total;
        this.timestamp = new Date();
    }
}
