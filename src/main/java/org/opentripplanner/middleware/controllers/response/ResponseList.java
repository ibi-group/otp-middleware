package org.opentripplanner.middleware.controllers.response;

import java.util.Date;
import java.util.List;

/**
 * Generic class for wrapping a paginated list response for a 'get all' HTTP endpoint.
 */
public class ResponseList<T> {
    public final List<T> data;
    public final int page;
    public final int limit;
    public final int total;
    public final Date timestamp;

    /**
     * Constructor for generating a paginated response list.
     * @param data - list of entities to limit
     * @param page - from index to abbreviate data
     * @param limit - number of entities to include in list
     */
    public ResponseList(List<T> data, int page, int limit){
        this.page = page;
        this.limit = limit;
        this.total = data.size();
        this.data = data.subList(page * limit, Math.min(this.total, (page + 1) * limit));
        this.timestamp = new Date();
    }
}
