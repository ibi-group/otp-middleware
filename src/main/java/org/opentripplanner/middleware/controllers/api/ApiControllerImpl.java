package org.opentripplanner.middleware.controllers.api;

import org.opentripplanner.middleware.models.Model;
import org.opentripplanner.middleware.persistence.TypedPersistence;

/**
 * Provides a basic implementation of the {@link ApiController} abstract class.
 */
public class ApiControllerImpl<T extends Model> extends ApiController {
    public ApiControllerImpl(String apiPrefix, TypedPersistence typedPersistence){
        super(apiPrefix, typedPersistence);
    }
}
