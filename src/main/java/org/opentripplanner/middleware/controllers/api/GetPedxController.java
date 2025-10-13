package org.opentripplanner.middleware.controllers.api;

import io.github.manusant.ss.SparkSwagger;
import io.github.manusant.ss.descriptor.EndpointDescriptor;
import io.github.manusant.ss.rest.Endpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.triptracker.interactions.UsGdotGwinnettTrafficSignalNotifier;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;

import static io.github.manusant.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * G-MAP -specific. Endpoint to ping pedestrial signal controller (retrieves data about one intersection).
 */
public class GetPedxController implements Endpoint {
    private final String ROOT_ROUTE;

    public GetPedxController(String apiPrefix) {
        this.ROOT_ROUTE = apiPrefix + "secure/pedx";
    }

    /**
     * Register the API endpoint and GET resource to retrieve API usage logs
     * when spark-swagger calls this function with the target API instance.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        restApi.endpoint(
            EndpointDescriptor.endpointPath(ROOT_ROUTE).withDescription("Get PEDX."),
            HttpUtils.NO_FILTER
        ).get(path(ROOT_ROUTE)
                .withDescription("Get PEDX.")
                .withProduces(HttpUtils.JSON_ONLY),
            GetPedxController::getPedx, JsonUtils::toJson);
    }

    /**
     * HTTP endpoint to return the usage (number of requests made/requests remaining) for the AWS API Gateway usage
     * plans. Defaults to the last 30 days for all API keys in the AWS account.
     */
    private static String getPedx(Request req, Response res) {
        UsGdotGwinnettTrafficSignalNotifier notifier = new UsGdotGwinnettTrafficSignalNotifier();
        int result = notifier.testIntersection("713");

        if (result == HttpStatus.OK_200) {
            return "OK";
        } else {
            logMessageAndHalt(req, result, "Unable to get intersection info");
        }
        return null;
    }
}
