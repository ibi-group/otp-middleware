package org.opentripplanner.middleware.controllers.api;

import com.beerboy.spark.typify.route.GsonRoute;
import com.beerboy.ss.SparkSwagger;
import com.beerboy.ss.rest.Endpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.models.Model;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.spark.Main;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;

import java.util.Date;
import java.util.List;

import static com.beerboy.ss.descriptor.EndpointDescriptor.endpointPath;
import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static com.beerboy.ss.rest.RestResponse.ok;
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
public abstract class ApiController<T extends Model> implements Endpoint {
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
        if (!Main.DO_SWAGGER) registerRoutes();
    }

    /**
     * This method is called by spark-swagger to register endpoints and generate the docs.
     * @param restApi The object to which to attach the documentation.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        LOG.info("Registering routes and enabling docs for {}", ROOT_ROUTE);

        restApi.endpoint(endpointPath(ROOT_ROUTE)
                .withDescription("Interface for querying and managing '" + classToLowercase + "' entities."), (q, a) -> LOG.info("Received request for '{}' Rest API", classToLowercase))


                // If using lambdas with the GET method, a bug in the documentation framework
                // requires you to write path(<entire_route>).
                // If you use new GsonRoute() {...} with the GET method, you only need to write path(<relative_to_endpointPath>).
                // Other HTTP methods are not affected by this bug.

                // Get multiple entities.
                .get(path(ROOT_ROUTE)
                        .withDescription("Gets a list of all '" + classToLowercase + "' entities.")
                        .withResponseAsCollection(clazz)
                        ,
                        this::getMany, JsonUtils::toJson
                )

                // Get one entity.
                .get(path(ROOT_ROUTE + ID_PARAM)
                        .withDescription("Returns a '" + classToLowercase + "' entity with the specified id, or 404 if not found.")
                        .withPathParam().withName("id").withDescription("The id of the entity to search.").and()
                        .withResponseType(clazz)
                        // .withResponses(...) // FIXME: not implemented (requires source change).
                        ,
                        this::getOne, JsonUtils::toJson
                )

                // Options request.
                .options(path(""), (req, res) -> "")

                // Create entity request
                .post(path("")
                        .withDescription("Creates a '" + classToLowercase + "' entity.")
                        .withRequestType(clazz) // FIXME: Embedded Swagger UI doesn't work for this request. (Embed or link a more recent version?)
                        .withResponseType(clazz)
                        ,
                        this::createOrUpdate, JsonUtils::toJson
                )

                // Update entity request
                .put(path(ID_PARAM)
                        .withDescription("Updates and returns the '" + classToLowercase + "' entity with the specified id, or 404 if not found.")
                        .withPathParam().withName("id").withDescription("The id of the entity to update.").and()
                        .withRequestType(clazz) // FIXME: Embedded Swagger UI doesn't work for this request. (Embed or link a more recent version?)
                        .withResponseType(clazz)
                        // .withResponses(...) // FIXME: not implemented (requires source change).
                        ,
                        this::createOrUpdate, JsonUtils::toJson
                )

                // Delete entity request
                .delete(path(ID_PARAM)
                        .withDescription("Deletes the '" + classToLowercase + "' entity with the specified id if it exists.")
                        .withPathParam().withName("id").withDescription("The id of the entity to delete.").and()
                        .withGenericResponse()
                        ,
                        this::deleteOne, JsonUtils::toJson
                );
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
    private List<T> getMany(Request req, Response res) {
        return persistence.getAll();
    }

    /**
     * HTTP endpoint to get one entity specified by ID.
     */
    private T getOne(Request req, Response res) {
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
            logMessageAndHalt(req, 400, String.format("Error deleting %s", classToLowercase), e);
        } finally {
            LOG.info("Delete operation took {} msec", System.currentTimeMillis() - startTime);
        }
        return null;
    }

    /**
     * Convenience method for extracting the ID param from the HTTP request.
     */
    private T getObjectForId(Request req, String id) {
        T object = (T) persistence.getById(id);
        if (object == null) {
            logMessageAndHalt(
                req,
                HttpStatus.BAD_REQUEST_400,
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
    private T createOrUpdate(Request req, Response res) {
        long startTime = System.currentTimeMillis();
        // Check if an update or create operation depending on presence of id param
        // This needs to be final because it is used in a lambda operation below.
        if (req.params("id") == null && req.requestMethod().equals("PUT")) {
            logMessageAndHalt(req, 400, "Must provide id");
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
                if (!id.equals(object.id)) {
                    logMessageAndHalt(req, 400, "Must provide ID in JSON body.");
                }
                persistence.replace(id, object);
            }
            // Return object that ultimately gets stored in database.
            return (T) persistence.getById(object.id);
        } catch (HaltException e) {
            throw e;
        } catch (Exception e) {
            logMessageAndHalt(req, 500, "An error was encountered while trying to save to the database", e);
        } finally {
            String operation = isCreating ? "Create" : "Update";
            LOG.info("{} operation took {} msec", operation, System.currentTimeMillis() - startTime);
        }
        return null;
    }

    /**
     * Get entity ID from request.
     */
    private String getIdFromRequest(Request req) {
        String id = req.params("id");
        if (id == null) {
            logMessageAndHalt(req, 400, "Must provide id");
        }
        return id;
    }
}
