package org.opentripplanner.middleware.controllers.api;

import com.beerboy.ss.ApiEndpoint;
import com.beerboy.ss.SparkSwagger;
import com.beerboy.ss.rest.Endpoint;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.models.Model;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.TypedPersistence;
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
import static org.opentripplanner.middleware.auth.Auth0Connection.getUserFromRequest;
import static org.opentripplanner.middleware.auth.Auth0Connection.isUserAdmin;
import static org.opentripplanner.middleware.utils.JsonUtils.getPOJOFromRequestBody;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

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
    protected final String ROOT_ROUTE;
    private static final String SECURE = "secure/";
    protected static final Logger LOG = LoggerFactory.getLogger(ApiController.class);
    private final String classToLowercase;
    final TypedPersistence<T> persistence;
    private final Class<T> clazz;

    /**
     * @param apiPrefix string prefix to use in determining the resource location
     * @param persistence {@link TypedPersistence} persistence for the entity to set up CRUD endpoints for
     */
    public ApiController (String apiPrefix, TypedPersistence<T> persistence) {
        this(apiPrefix, persistence, null);
    }

    public ApiController (String apiPrefix, TypedPersistence<T> persistence, String resource) {
        this.clazz = persistence.clazz;
        this.persistence = persistence;
        this.classToLowercase = persistence.clazz.getSimpleName().toLowerCase();
        // Default resource to class name.
        if (resource == null) resource = SECURE + persistence.clazz.getSimpleName().toLowerCase();
        this.ROOT_ROUTE = apiPrefix + resource;
    }

    /**
     * This method is called on each object deriving from Endpoint by {@link SparkSwagger}
     * to register endpoints and generate the swagger documentation skeleton.
     * In this method, we add the different API paths and methods (e.g. the CRUD methods)
     * to the restApi parameter for the applicable controller.
     * @param restApi The object to which to attach the documentation.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        ApiEndpoint apiEndpoint = restApi.endpoint(
            endpointPath(ROOT_ROUTE).withDescription(String.format("API_TEMPLATE:%s", clazz.getSimpleName())),
            (q, a) -> LOG.info("Received request for '{}' Rest API", classToLowercase)
        );
        buildEndpoint(apiEndpoint);
    }

    /**
     * This method adds to the provided baseEndpoint parameter a set of basic HTTP Spark methods
     * (e.g., getOne, getMany, delete) for CRUD operations.
     * It can optionally be overridden by child classes to add any supplemental methods to the baseEndpoint.
     * Either before or after(*) supplemental methods are added, be sure to call the super method to add CRUD operations.
     *
     * (*) Note: spark-java will resolve methods in the order they are added to the baseEndpoint parameter.
     * For instance, if /path and /path/subpath are added in this order, then
     * a request with /path/subpath will be treated as /path, and the method for /path/subpath will be ignored.
     * Conversely, if /path/subpath and /path are added in this order, then
     * a request with /path/subpath will be handled by the method for /path/subpath.
     * @param baseEndpoint The end point to which to add the methods.
     */
    protected void buildEndpoint(ApiEndpoint baseEndpoint) {
        LOG.info("Registering routes and enabling docs for {}", ROOT_ROUTE);

        // Careful here!
        // If using lambdas with the GET method, a bug in spark-swagger
        // requires you to write path(<entire_route>).
        // If you use `new GsonRoute() {...}` with the GET method, you only need to write path(<relative_to_endpointPath>).
        // Other HTTP methods are not affected by this bug.

        // Beware of these omissions in spark-swagger that affect the code below:
        // - withResponses will not be processed (Only '200' responses will be generated).
        //   See https://github.com/manusant/spark-swagger/blob/master/src/main/java/io/github/manusant/ss/Swagger.java#L518.
        // - withResponseAsCollection generates the provided type, but not the type for the array/collection.
        //   See https://github.com/manusant/spark-swagger/blob/master/src/main/java/io/github/manusant/ss/Swagger.java#L499.
        // - fields from parent classes are ignored.

        baseEndpoint
            // Get multiple entities.
            .get(path(ROOT_ROUTE).withResponseAsCollection(clazz), this::getMany, JsonUtils::toJson)

            // Get one entity.
            .get(path(ROOT_ROUTE + ID_PARAM).withResponseType(clazz), this::getOne, JsonUtils::toJson)

            // Options response for CORS
            .options(path(""), (req, res) -> "")

            // Create entity request
            .post(path("").withResponseType(clazz), this::createOrUpdate, JsonUtils::toJson)

            // Update entity request
            .put(path(ID_PARAM).withResponseType(clazz), this::createOrUpdate, JsonUtils::toJson)

            // Delete entity request
            .delete(path(ID_PARAM), this::deleteOne, JsonUtils::toJson);
    }

    /**
     * HTTP endpoint to get multiple entities based on the user permissions
     */
    // FIXME Maybe better if the user check (and filtering) was done in a pre hook?
    // FIXME Will require further granularity for admin
    private List<T> getMany(Request req, Response res) {

        Auth0UserProfile requestingUser = getUserFromRequest(req);
        if (isUserAdmin(requestingUser)) {
            // If the user is admin, the context is presumed to be the admin dashboard, so we deliver all entities for
            // management or review without restriction.
            return persistence.getAll();
        } else if (persistence.clazz == OtpUser.class) {
            // If the required entity is of type 'OtpUser' the assumption is that a call is being made via the
            // OtpUserController. Therefore, the request should be limited to return just the entity matching the
            // requesting user.
            return getObjectsFiltered("_id", requestingUser.otpUser.id);
        } else {
            // For all other cases the assumption is that the request is being made by an Otp user and the requested
            // entities have a 'userId' parameter. Only entities that match the requesting user id are returned.
            return getObjectsFiltered("userId", requestingUser.otpUser.id);
        }
    }

    /**
     * Get a list of objects filtered by the provided field name and value.
     */
    private List<T> getObjectsFiltered(String fieldName, String value) {
        Bson filter = Filters.eq(fieldName, value);
        return persistence.getFiltered(filter);
    }

    /**
     * HTTP endpoint to get one entity specified by ID. This will return an object based on the checks carried out in
     * the overridden 'canBeManagedBy' method. It is the responsibility of this method to define access to it's own
     * object. The default behaviour is defined in {@link Model#canBeManagedBy} and may have too restrictive access
     * (must be admin) than is desired.
     */
    private T getOne(Request req, Response res) {
        Auth0UserProfile requestingUser = Auth0Connection.getUserFromRequest(req);
        String id = getIdFromRequest(req);
        T object = getObjectForId(req, id);

        if (!object.canBeManagedBy(requestingUser)) {
            logMessageAndHalt(req, HttpStatus.FORBIDDEN_403, String.format("Requesting user not authorized to get %s.", classToLowercase));
        }

        return object;
    }

    /**
     * HTTP endpoint to delete one entity specified by ID.
     */
    private String deleteOne(Request req, Response res) {
        long startTime = System.currentTimeMillis();
        String id = getIdFromRequest(req);
        Auth0UserProfile requestingUser = Auth0Connection.getUserFromRequest(req);
        try {
            T object = getObjectForId(req, id);
            // Check that requesting user can manage entity.
            if (!object.canBeManagedBy(requestingUser)) {
                logMessageAndHalt(req, HttpStatus.FORBIDDEN_403, String.format("Requesting user not authorized to delete %s.", classToLowercase));
            }
            // Run pre-delete hook. If return value is false, abort.
            if (!preDeleteHook(object, req)) {
                logMessageAndHalt(req, 500, "Unknown error occurred during delete attempt.");
            }
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
    private T getObjectForId(Request req, String id) {
        T object = persistence.getById(id);
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
     * Hook called before object is created in MongoDB.
     */
    abstract T preCreateHook(T entityToCreate, Request req);

    /**
     * Hook called before object is updated in MongoDB. Validation of entity object could go here.
     */
    abstract T preUpdateHook(T entityToUpdate, T preExistingEntity, Request req);

    /**
     * Hook called before object is deleted in MongoDB.
     */
    abstract boolean preDeleteHook(T entity, Request req);

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
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "Must provide id");
        }
        Auth0UserProfile requestingUser = Auth0Connection.getUserFromRequest(req);
        final boolean isCreating = req.params("id") == null;
        // Save or update to database
        try {
            // Validate fields by deserializing into POJO.
            T object = getPOJOFromRequestBody(req, clazz);
            if (isCreating) {
                // Verify that the requesting user can create object.
                if (!object.canBeCreatedBy(requestingUser)) {
                    logMessageAndHalt(req, HttpStatus.FORBIDDEN_403, String.format("Requesting user not authorized to create %s.", classToLowercase));
                }
                // Run pre-create hook and use updated object (with potentially modified values) in create operation.
                T updatedObject = preCreateHook(object, req);
                persistence.create(updatedObject);
            } else {
                String id = getIdFromRequest(req);
                T preExistingObject = getObjectForId(req, id);
                if (preExistingObject == null) {
                    logMessageAndHalt(req, 400, "Object to update does not exist!");
                    return null;
                }
                // Check that requesting user can manage entity.
                if (!preExistingObject.canBeManagedBy(requestingUser)) {
                    logMessageAndHalt(req, HttpStatus.FORBIDDEN_403, String.format("Requesting user not authorized to update %s.", classToLowercase));
                }
                // Update last updated value.
                object.lastUpdated = new Date();
                // Pin the date created to pre-existing value.
                object.dateCreated = preExistingObject.dateCreated;
                // Validate that ID in JSON body matches ID param. TODO add test
                if (!id.equals(object.id)) {
                    logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "ID in JSON body must match ID param.");
                }
                // Get updated object from pre-update hook method.
                T updatedObject = preUpdateHook(object, preExistingObject, req);
                persistence.replace(id, updatedObject);
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
        return getRequiredParamFromRequest(req, "id");
    }

    /**
     * Get a request parameter value.
     * This method will halt the request if paramName is not provided in the request.
     */
    private String getRequiredParamFromRequest(Request req, String paramName) {
        String paramValue = req.params(paramName);
        if (paramValue == null) {
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "Must provide parameter name.");
        }
        return paramValue;
    }
}
