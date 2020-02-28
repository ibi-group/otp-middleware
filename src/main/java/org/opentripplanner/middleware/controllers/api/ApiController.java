package org.opentripplanner.middleware.controllers.api;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.Model;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;

import java.util.Date;
import java.util.List;

import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromRequestBody;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;
import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.options;
import static spark.Spark.post;
import static spark.Spark.put;

/**
 * Generic API controller abstract class. This class provides CRUD methods using {@link spark.Spark} HTTP request
 * methods. This will identify the MongoDB collection on which to operate based on the provided {@link Model} class.
 *
 * TODO: Add hooks so that validation can be performed on certain methods (e.g., validating fields on create/update,
 *  checking user permissions to perform certain actions, checking whether an entity can be deleted due to references
 *  that exist in other collection).
 * @param <T> One of the {@link Model} classes (extracted from {@link TypedPersistence})
 */
public abstract class ApiController<T extends Model> {
    private static final String ID_PARAM = "/:id";
    private final String ROOT_ROUTE;
    private static final String SECURE = "secure/";
    private static final Logger LOG = LoggerFactory.getLogger(ApiController.class);
    private final String classToLowercase;
    private final TypedPersistence persistence;
    private final Class<T> clazz;

    /**
     * @param apiPrefix string prefix to use in determining the resource location
     * @param persistence {@link TypedPersistence} persistence for the entity to set up CRUD endpoints for
     */
    public ApiController (String apiPrefix, TypedPersistence persistence) {
        this.clazz = persistence.clazz;
        this.persistence = persistence;
        this.classToLowercase = persistence.clazz.getSimpleName().toLowerCase();
        this.ROOT_ROUTE = apiPrefix + SECURE + classToLowercase;
        registerRoutes();
    }

    /**
     * Register basic CRUD HTTP endpoints for the controller implementation.
     */
    private void registerRoutes() {
        LOG.info("Registering routes for {}", ROOT_ROUTE);
        // Options response for CORS
        options(ROOT_ROUTE, (q, s) -> "");
        // Get multiple entities.
        get(ROOT_ROUTE, this::getMany, JsonUtils::toJson);
        // Get one entity.
        get(ROOT_ROUTE + ID_PARAM, this::getOne, JsonUtils::toJson);
        // Create entity request
        post(ROOT_ROUTE, this::createOrUpdate, JsonUtils::toJson);
        // Update entity request
        put(ROOT_ROUTE + ID_PARAM, this::createOrUpdate, JsonUtils::toJson);
        // Delete entity request
        delete(ROOT_ROUTE + ID_PARAM, this::deleteOne, JsonUtils::toJson);
    }

    /**
     * HTTP endpoint to get multiple entities.
     */
    private List getMany(Request req, Response res) {
        return persistence.getAll();
    }

    /**
     * HTTP endpoint to get one entity specified by ID.
     */
    private Model getOne(Request req, Response res) {
        String id = getIdFromRequest(req);
        return getObjectForId(req, id);
    }

    /**
     * HTTP endpoint to delete one entity specified by ID.
     */
    private String deleteOne(Request req, Response res) {
        long startTime = System.currentTimeMillis();
        String id = getIdFromRequest(req);
        try {
            getObjectForId(req, id);
            boolean success = persistence.removeById(id);
            int code = success ? HttpStatus.OK_200 : HttpStatus.INTERNAL_SERVER_ERROR_500;
            String message = success
                ? String.format("Successfully deleted %s.", classToLowercase)
                : String.format("Failed to delete %s", classToLowercase);
            logMessageAndHalt(req, code, message, null);
        } catch (HaltException e) {
            throw e;
        } catch (Exception e) {
            logMessageAndHalt(
                req,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                String.format("Error deleting %s", classToLowercase),
                e
            );
        } finally {
            LOG.info("Delete operation took {} msec", System.currentTimeMillis() - startTime);
        }
        return null;
    }

    /**
     * Convenience method for extracting the ID param from the HTTP request.
     */
    private Model getObjectForId(Request req, String id) {
        Model object = persistence.getById(id);
        if (object == null) {
            logMessageAndHalt(
                req,
                HttpStatus.NOT_FOUND_404,
                String.format("No %s with id=%s found.", classToLowercase, id),
                null
            );
        }
        return object;
    }

    /**
     * HTTP endpoint to create or update a single entity. If the ID param is supplied and the HTTP method is
     * PUT, an update operation will be applied to the specified entity using the JSON body found in the request.
     * Otherwise, a new entity will be created.
     */
    private Model createOrUpdate(Request req, Response res) {
        long startTime = System.currentTimeMillis();
        // Check if an update or create operation depending on presence of id param
        // This needs to be final because it is used in a lambda operation below.
        if (req.params("id") == null && req.requestMethod().equals("PUT")) {
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "Must provide id");
        }
        final boolean isCreating = req.params("id") == null;
        // Save or update to database
        try {
            // Validate fields by deserializing into POJO.
            Model object = getPOJOFromRequestBody(req, clazz);
            // TODO Add validation hooks for specific models... e.g., enforcing unique emails for users, checking
            //  valid email addresses, etc.
            if (isCreating) {
                persistence.create(object);
            } else {
                String id = getIdFromRequest(req);
                // Update last updated value.
                object.lastUpdated = new Date();
                object.dateCreated = getObjectForId(req, id).dateCreated;
                // Validate that ID in JSON body matches ID param. TODO add test
                if (!id.equals(object.id)) {
                    logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "ID in JSON body must match ID param.");
                }
                persistence.replace(id, object);
            }
            // Return object that ultimately gets stored in database.
            return persistence.getById(object.id);
        } catch (HaltException e) {
            throw e;
        } catch (Exception e) {
            logMessageAndHalt(req, 500, "An error was encountered while trying to save to the database", e);
        } finally {
            String operation = isCreating ? "Create" : "Update";
            LOG.info("{} {} operation took {} msec", operation, classToLowercase, System.currentTimeMillis() - startTime);
        }
        return null;
    }

    /**
     * Get entity ID from request.
     */
    private String getIdFromRequest(Request req) {
        String id = req.params("id");
        if (id == null) {
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "Must provide id");
        }
        return id;
    }
}
