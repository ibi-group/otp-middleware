package org.opentripplanner.middleware.controllers.api;

import io.github.manusant.ss.ApiEndpoint;
import io.github.manusant.ss.SparkSwagger;
import io.github.manusant.ss.descriptor.ParameterDescriptor;
import io.github.manusant.ss.rest.Endpoint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.controllers.response.ResponseList;
import org.opentripplanner.middleware.models.Model;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.SwaggerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;

import java.util.Map;

import static io.github.manusant.ss.descriptor.EndpointDescriptor.endpointPath;
import static io.github.manusant.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.utils.HttpUtils.getRequiredParamFromRequest;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Generic API controller abstract class. This class provides CRUD methods using {@link spark.Spark} HTTP request
 * methods. This will identify the MongoDB collection on which to operate based on the provided {@link Model} class.
 *
 * TODO: Add hooks so that validation can be performed on certain methods (e.g., validating fields on create/update,
 *  checking user permissions to perform certain actions, checking whether an entity can be deleted due to references
 *  that exist in other collection).
 *
 * @param <T> One of the {@link Model} classes (extracted from {@link TypedPersistence})
 */
public abstract class ApiController<T extends Model> implements Endpoint {
    protected static final String ID_PARAM = "id";
    protected static final String ID_PATH = "/:" + ID_PARAM;
    protected final String ROOT_ROUTE;
    private static final String SECURE = "secure/";
    private final String className;
    private static final Logger LOG = LoggerFactory.getLogger(ApiController.class);
    final TypedPersistence<T> persistence;
    private final Class<T> clazz;
    public static final String LIMIT_PARAM = "limit";
    public static final int DEFAULT_LIMIT = 10;
    public static final int DEFAULT_OFFSET = 0;
    public static final String OFFSET_PARAM = "offset";
    public static final String USER_ID_PARAM = "userId";

    public static final ParameterDescriptor LIMIT = ParameterDescriptor.newBuilder()
        .withName(LIMIT_PARAM)
        .withDefaultValue(String.valueOf(DEFAULT_LIMIT))
        .withDescription("If specified, the maximum number of items to return.").build();
    public static final ParameterDescriptor OFFSET = ParameterDescriptor.newBuilder()
        .withName(OFFSET_PARAM)
        .withDefaultValue(String.valueOf(DEFAULT_OFFSET))
        .withDescription("If specified, the number of records to skip/offset.").build();
    public static final ParameterDescriptor USER_ID = ParameterDescriptor.newBuilder()
        .withName(USER_ID_PARAM)
        .withRequired(false)
        .withDescription("If specified, the required user id.").build();
    protected final Map<String, io.github.manusant.ss.model.Response> stdResponses;

    /**
     * @param apiPrefix string prefix to use in determining the resource location
     * @param persistence {@link TypedPersistence} persistence for the entity to set up CRUD endpoints for
     */
    public ApiController(String apiPrefix, TypedPersistence<T> persistence) {
        this(apiPrefix, persistence, null);
    }

    public ApiController(String apiPrefix, TypedPersistence<T> persistence, String resource) {
        // Ensure that typed persistence has been constructed in Persistence#initialize
        if (persistence == null) {
            throw new IllegalArgumentException("Mongo collection must be initialized in Persistence.java");
        }
        this.clazz = persistence.clazz;
        this.persistence = persistence;
        this.className = persistence.clazz.getSimpleName();
        // Default resource to class name.
        if (resource == null) resource = SECURE + className.toLowerCase();
        this.ROOT_ROUTE = apiPrefix + resource;

        this.stdResponses = SwaggerUtils.createStandardResponses(clazz);
    }

    /**
     * This method is called by {@link SparkSwagger} on each object that implements Endpoint
     * to register endpoints and generate the swagger documentation.
     * In this method, we add the different API paths and methods (e.g. the CRUD methods)
     * to the restApi parameter for the applicable controller.
     *
     * @param restApi The object to which to attach the documentation.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        ApiEndpoint apiEndpoint = restApi.endpoint(
            endpointPath(ROOT_ROUTE)
                .withDescription("Interface for querying and managing '" + className + "' entities."),
            HttpUtils.NO_FILTER
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
     *
     * @param baseEndpoint The end point to which to add the methods.
     */
    protected void buildEndpoint(ApiEndpoint baseEndpoint) {
        LOG.info("Registering routes and enabling docs for {}", ROOT_ROUTE);

        // Careful here!
        // If using lambdas with the GET method, a bug in spark-swagger 1.x and 2.0.2
        // requires you to write path(<entire_route>).
        // If you use `new GsonRoute() {...}` with the GET method, you only need to write path(<relative_to_endpointPath>).
        // Other HTTP methods are not affected by this bug.

        baseEndpoint
            // Get multiple entities.
            .get(path(ROOT_ROUTE)
                    .withDescription("Gets a paginated list of all '" + className + "' entities.")
                    .withQueryParam(LIMIT)
                    .withQueryParam(OFFSET)
                    .withQueryParam(USER_ID)
                    .withProduces(HttpUtils.JSON_ONLY)
                    .withResponseType(ResponseList.class),
                this::getMany, JsonUtils::toJson
            )

            // Get one entity.
            .get(path(ROOT_ROUTE + ID_PATH)
                    .withDescription("Returns the '" + className + "' entity with the specified id, or 404 if not found.")
                    .withPathParam().withName(ID_PARAM).withRequired(true).withDescription("The id of the entity to search.").and()
                    .withProduces(HttpUtils.JSON_ONLY)
                    .withResponses(stdResponses),
                this::getEntityForId, JsonUtils::toJson
            )

            // Create entity request
            .post(path("")
                    .withDescription("Creates a '" + className + "' entity.")
                    .withConsumes(HttpUtils.JSON_ONLY)
                    .withRequestType(clazz)
                    .withProduces(HttpUtils.JSON_ONLY)
                    .withResponses(stdResponses),
                this::createOrUpdate, JsonUtils::toJson
            )

            // Update entity request
            .put(path(ID_PATH)
                    .withDescription("Updates and returns the '" + className + "' entity with the specified id, or 404 if not found.")
                    .withPathParam().withName(ID_PARAM).withRequired(true).withDescription("The id of the entity to update.").and()
                    .withConsumes(HttpUtils.JSON_ONLY)
                    .withRequestType(clazz)
                    .withProduces(HttpUtils.JSON_ONLY)
                    .withResponses(stdResponses),
                this::createOrUpdate, JsonUtils::toJson
            )

            // Delete entity request
            .delete(path(ID_PATH)
                    .withDescription("Deletes the '" + className + "' entity with the specified id if it exists.")
                    .withPathParam().withName(ID_PARAM).withRequired(true).withDescription("The id of the entity to delete.").and()
                    .withProduces(HttpUtils.JSON_ONLY)
                    .withResponses(stdResponses),
                this::deleteOne, JsonUtils::toJson
            );
    }

    /**
     * HTTP endpoint to get multiple entities based on the user permissions
     */
    // FIXME Maybe better if the user check (and filtering) was done in a pre hook?
    // FIXME Will require further granularity for admin
    private ResponseList<T> getMany(Request req, Response res) {
        int limit = HttpUtils.getQueryParamFromRequest(req, LIMIT_PARAM, 0, DEFAULT_LIMIT, 100);
        int offset = HttpUtils.getQueryParamFromRequest(req, OFFSET_PARAM, 0, DEFAULT_OFFSET);
        String userId = HttpUtils.getQueryParamFromRequest(req, USER_ID_PARAM, true);
        // Filter the response based on the user id, if provided.
        // If the user id is not provided filter response based on requesting user.
        RequestingUser requestingUser = Auth0Connection.getUserFromRequest(req);
        if (userId != null) {
            OtpUser otpUser = Persistence.otpUsers.getById(userId);
            if (requestingUser.canManageEntity(otpUser)) {
                return persistence.getResponseList(Filters.eq(USER_ID_PARAM, userId), offset, limit);
            } else {
                res.status(HttpStatus.FORBIDDEN_403);
                return null;
            }
        }
        if (requestingUser.isAdmin()) {
            // If the user is admin, the context is presumed to be the admin dashboard, so we deliver all entities for
            // management or review without restriction.
            return persistence.getResponseList(offset, limit);
        } else if (persistence.clazz == OtpUser.class) {
            // If the required entity is of type 'OtpUser' the assumption is that a call is being made via the
            // OtpUserController. If the request is being made by an Api user the response will be limited to the Otp users
            // created by this Api user. If not, the assumption is that an Otp user is making the request and the response
            // will be limited to just the entity matching this Otp user.
            Bson filter = (requestingUser.apiUser != null)
                ? Filters.eq("applicationId", requestingUser.apiUser.id)
                : Filters.eq("_id", requestingUser.otpUser.id);
            return persistence.getResponseList(filter, offset, limit);
        } else if (requestingUser.isAPIUser()) {
            // A user id must be provided if the request is being made by a third party user.
            logMessageAndHalt(req,
                HttpStatus.BAD_REQUEST_400,
                String.format("The parameter name (%s) must be provided.", USER_ID_PARAM));
            return null;
        } else {
            // For all other cases the assumption is that the request is being made by an Otp user and the requested
            // entities have a 'userId' parameter. Only entities that match the requesting user id are returned.
            return persistence.getResponseList(Filters.eq(USER_ID_PARAM, requestingUser.otpUser.id), offset, limit);
        }
    }

    /**
     * HTTP endpoint to get one entity specified by ID. This will return an object based on the checks carried out in
     * the overridden 'canBeManagedBy' method. It is the responsibility of this method to define access to it's own
     * object. The default behaviour is defined in {@link Model#canBeManagedBy} and may have too restrictive access
     * (must be admin) than is desired.
     */
    protected T getEntityForId(Request req, Response res) {
        RequestingUser requestingUser = Auth0Connection.getUserFromRequest(req);
        String id = getIdFromRequest(req);
        T object = getObjectForId(req, id);

        if (!requestingUser.canManageEntity(object)) {
            logMessageAndHalt(req, HttpStatus.FORBIDDEN_403, String.format("Requesting user not authorized to get %s.", className));
        }

        return object;
    }

    /**
     * HTTP endpoint to delete one entity specified by ID.
     */
    private T deleteOne(Request req, Response res) {
        long startTime = DateTimeUtils.currentTimeMillis();
        String id = getIdFromRequest(req);
        RequestingUser requestingUser = Auth0Connection.getUserFromRequest(req);
        try {
            T object = getObjectForId(req, id);
            // Check that requesting user can manage entity.
            if (!requestingUser.canManageEntity(object)) {
                logMessageAndHalt(req, HttpStatus.FORBIDDEN_403, String.format("Requesting user not authorized to delete %s.", className));
            }
            // Run pre-delete hook. If return value is false, abort.
            if (!preDeleteHook(object, req)) {
                logMessageAndHalt(req, 500, "Unknown error occurred during delete attempt.");
            }
            boolean success = object.delete();
            if (success) {
                return object;
            } else {
                logMessageAndHalt(
                    req,
                    HttpStatus.INTERNAL_SERVER_ERROR_500,
                    String.format("Unknown error encountered. Failed to delete %s", className),
                    null
                );
            }
        } catch (HaltException e) {
            throw e;
        } catch (Exception e) {
            logMessageAndHalt(
                req,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                String.format("Error deleting %s", className),
                e
            );
        } finally {
            LOG.info("Delete operation took {} msec", DateTimeUtils.currentTimeMillis() - startTime);
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
                String.format("No %s with id=%s found.", className, id),
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
     * Hook called after object is created in MongoDB.
     */
    T postCreateHook(T object, Request req) { return object; }

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
        long startTime = DateTimeUtils.currentTimeMillis();
        // Check if an update or create operation depending on presence of id param
        // This needs to be final because it is used in a lambda operation below.
        if (req.params(ID_PARAM) == null && req.requestMethod().equals("PUT")) {
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "Must provide id");
        }
        RequestingUser requestingUser = Auth0Connection.getUserFromRequest(req);
        final boolean isCreating = req.params(ID_PARAM) == null;
        // Save or update to database
        try {
            // Validate fields by deserializing into POJO.
            T newEntity = JsonUtils.getPOJOFromRequestBody(req, clazz);
            if (isCreating) {
                // Verify that the requesting user can create object.
                if (!newEntity.canBeCreatedBy(requestingUser)) {
                    logMessageAndHalt(req,
                        HttpStatus.FORBIDDEN_403,
                        String.format("Requesting user not authorized to create %s.", className));
                }
                // Run pre-create hook and use updated object (with potentially modified values) in create operation.
                T updatedEntity = preCreateHook(newEntity, req);
                persistence.create(updatedEntity);
                postCreateHook(updatedEntity, req);
            } else {
                String id = getIdFromRequest(req);
                T preExistingEntity = getObjectForId(req, id);
                if (preExistingEntity == null) {
                    logMessageAndHalt(req, 400, "Object to update does not exist!");
                    return null;
                }
                // Check that requesting user can manage entity. This covers existing and new entities.
                if (!requestingUser.canManageEntity(preExistingEntity) || !requestingUser.canManageEntity(newEntity)) {
                    logMessageAndHalt(req,
                        HttpStatus.FORBIDDEN_403,
                        String.format("Requesting user not authorized to update %s.", className));
                }
                // Update last updated value.
                newEntity.lastUpdated = DateTimeUtils.nowAsDate();
                // Pin the date created to pre-existing value.
                newEntity.dateCreated = preExistingEntity.dateCreated;
                // Validate that ID in JSON body matches ID param. TODO add test
                if (!id.equals(newEntity.id)) {
                    logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "ID in JSON body must match ID param.");
                }
                // Get updated object from pre-update hook method.
                persistence.replace(id, preUpdateHook(newEntity, preExistingEntity, req));
            }
            // Return object that ultimately gets stored in database.
            return persistence.getById(newEntity.id);
        } catch (HaltException e) {
            throw e;
        } catch (JsonProcessingException e) {
            logMessageAndHalt(req,
                HttpStatus.BAD_REQUEST_400,
                "Error parsing JSON for " + clazz.getSimpleName(), e);
        } catch (Exception e) {
            logMessageAndHalt(req,
                500,
                "An error was encountered while trying to save to the database", e);
        } finally {
            String operation = isCreating ? "Create" : "Update";
            LOG.info("{} {} operation took {} msec", operation, className, DateTimeUtils.currentTimeMillis() - startTime);
        }
        return null;
    }

    /**
     * Get entity ID from request.
     */
    private String getIdFromRequest(Request req) {
        return getRequiredParamFromRequest(req, "id");
    }
}
