package org.opentripplanner.middleware.controllers.api;

import com.beerboy.ss.SparkSwagger;
import com.beerboy.ss.rest.Endpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.controllers.response.ResponseList;
import org.opentripplanner.middleware.utils.CDPFile;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;

import java.util.List;

import static com.beerboy.ss.descriptor.EndpointDescriptor.endpointPath;
import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.controllers.api.ApiController.LIMIT;
import static org.opentripplanner.middleware.controllers.api.ApiController.OFFSET;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.HttpUtils.JSON_ONLY;
import static org.opentripplanner.middleware.utils.S3Utils.getFolderListing;

public class CDPFilesController implements Endpoint {
    private final String ROOT_ROUTE;
    private static final String SECURE = "secure/";

    public static final String CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME =
            getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME");
    public static final String CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME =
            getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME");

    public CDPFilesController(String apiPrefix) {
        this.ROOT_ROUTE = apiPrefix + SECURE + "connected-data";
    }

    /**
     * Register the API endpoint and GET resource to retrieve CDP entries from S3
     * when spark-swagger calls this function with the target API instance.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        restApi.endpoint(
                endpointPath(ROOT_ROUTE).withDescription("Interface for listing and downloading CDP files from S3."),
                HttpUtils.NO_FILTER
        ).get(
                path(ROOT_ROUTE)
                        .withDescription("Gets a paginated list of CDP zip files in the configured S3 bucket.")
                        .withQueryParam(LIMIT)
                        .withQueryParam(OFFSET)
                        .withProduces(JSON_ONLY)
                        // Note: unlike what the name suggests, withResponseAsCollection does not generate an array
                        // as the return type for this method. (It does generate the type for that class nonetheless.)
                        .withResponseAsCollection(CDPFile.class),
                CDPFilesController::getAllFiles, JsonUtils::toJson);
    }

    /**
     * Get all zip files from the main S3 bucket, if user is a CDPUser or an admin.
     */
    private static ResponseList<CDPFile> getAllFiles(Request req, Response res) {
        // Check for permissions (admin user or CDP user)
        RequestingUser requestingUser = Auth0Connection.getUserFromRequest(req);
        if (requestingUser == null) {
            res.status(HttpStatus.BAD_REQUEST_400);
            return null;
        }
        if (!requestingUser.isCDPUser() && !requestingUser.isAdmin()) {
            res.status(HttpStatus.FORBIDDEN_403);
            return null;
        }
        List<CDPFile> cdpFiles = getFolderListing(CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME, CONNECTED_DATA_PLATFORM_S3_FOLDER_NAME);

        long count = cdpFiles.size();
        return new ResponseList<>(CDPFile.class, cdpFiles, 0, 0, count);
    }
}
