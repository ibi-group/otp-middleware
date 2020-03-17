package org.opentripplanner.middleware.controllers.api;

import org.opentripplanner.middleware.models.Model;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import spark.Request;

/**
 * Provides a basic implementation of the {@link ApiController} abstract class.
 */
public class ApiControllerImpl<T extends Model> extends ApiController<T> {
    public ApiControllerImpl(String apiPrefix, TypedPersistence typedPersistence){
        super(apiPrefix, typedPersistence);
    }

    @Override
    T preCreateHook(T entity, Request req) {
        return entity;
    }

    @Override
    T preUpdateHook(T entity, Request req) {
        return entity;
    }

    @Override
    boolean preDeleteHook(T entity, Request req) {
        return true;
    }
}
