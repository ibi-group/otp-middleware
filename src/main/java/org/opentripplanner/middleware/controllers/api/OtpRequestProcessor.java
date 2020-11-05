package org.opentripplanner.middleware.controllers.api;

import com.beerboy.ss.SparkSwagger;
import com.beerboy.ss.descriptor.EndpointDescriptor;
import com.beerboy.ss.descriptor.ParameterDescriptor;
import com.beerboy.ss.rest.Endpoint;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.TripRequest;
import org.opentripplanner.middleware.models.TripSummary;
import org.opentripplanner.middleware.otp.OtpDispatcher;
import org.opentripplanner.middleware.otp.OtpDispatcherResponse;
import org.opentripplanner.middleware.otp.response.OtpResponse;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

import javax.ws.rs.core.MediaType;
import java.util.List;

import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Responsible for getting a response from OTP based on the parameters provided by the requester. If the target service
 * is of interest the response is intercepted and processed. In all cases, the response from OTP (content and HTTP
 * status) is passed back to the requester.
 */
public class OtpRequestProcessor implements Endpoint {
    private static final Logger LOG = LoggerFactory.getLogger(OtpRequestProcessor.class);
    private static final String USER_ID_PARAM = "userId";

    /**
     * Endpoint for the OTP Middleware's OTP proxy
     */
    public static final String OTP_PROXY_ENDPOINT = "/otp";
    /**
     * URL to OTP's documentation.
     */
    private static final String OTP_DOC_URL = "http://otp-docs.ibi-transit.com/api/index.html";
    /**
     * Text that links to OTP's documentation for more info.
     */
    private static final String OTP_DOC_LINK = String.format(
        "Refer to <a href='%s'>OTP's API documentation</a> for OTP's supported API resources.",
        OTP_DOC_URL
    );

    /**
     * Register http endpoint with {@link spark.Spark} instance based on the OTP root endpoint. An OTP root endpoint is
     * required to distinguish between OTP and other middleware requests.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        ParameterDescriptor USER_ID = ParameterDescriptor.newBuilder()
            .withName(USER_ID_PARAM)
            .withRequired(false)
            .withDescription("If a third-party application is making a trip plan request on behalf of an end user (OtpUser), the user id must be specified.")
            .build();
        restApi.endpoint(
            EndpointDescriptor.endpointPath(OTP_PROXY_ENDPOINT).withDescription("Proxy interface for OTP endpoints. " + OTP_DOC_LINK),
            HttpUtils.NO_FILTER
        ).get(path("/*")
                .withDescription("Forwards any GET request to OTP. " + OTP_DOC_LINK)
                .withQueryParam(USER_ID)
                .withProduces(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML)),
            OtpRequestProcessor::proxy
        );
    }

    /**
     * Responsible for proxying any and all requests made to its HTTP endpoint to OTP. If the target service is of
     * interest (e.g., requests made to the plan trip endpoint are currently logged if the user has consented to storing
     * trip history) the response is intercepted and processed. In all cases, the response from OTP (content and HTTP
     * status) is passed back to the requester.
     */
    private static String proxy(Request request, spark.Response response) {
        if (OtpDispatcher.OTP_API_ROOT == null) {
            logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, "No OTP Server provided, check config.");
            return null;
        }
        // Get request path intended for OTP API by removing the proxy endpoint (/otp).
        String otpRequestPath = request.uri().replaceFirst(OTP_PROXY_ENDPOINT, "");

        // attempt to get response from OTP server based on requester's query parameters
        OtpDispatcherResponse otpDispatcherResponse = OtpDispatcher.sendOtpRequest(request.queryString(), otpRequestPath);
        if (otpDispatcherResponse == null || otpDispatcherResponse.responseBody == null) {
            logMessageAndHalt(request, HttpStatus.INTERNAL_SERVER_ERROR_500, "No response from OTP server.");
            return null;
        }

        // If the request path ends with the plan endpoint (e.g., '/plan' or '/default/plan'), process response.
        if (otpRequestPath.endsWith(OtpDispatcher.OTP_PLAN_ENDPOINT)) handlePlanTripResponse(request, otpDispatcherResponse);

        // provide response to requester as received from OTP server
        response.type(MediaType.APPLICATION_JSON);
        response.status(otpDispatcherResponse.statusCode);
        return otpDispatcherResponse.responseBody;
    }

    /**
     * Process plan response from OTP. Store the response if consent is given. Handle the process and all exceptions
     * seamlessly so as not to affect the response provided to the requester.
     */
    private static void handlePlanTripResponse(Request request, OtpDispatcherResponse otpDispatcherResponse) {

        // If the Auth header is present, this indicates that the request was made by a logged in user. If present
        // we should store trip history (but we verify this preference before doing so).
        if (!Auth0Connection.isAuthHeaderPresent(request)) {
            LOG.debug("Anonymous user, trip history not stored");
            return;
        }

        String batchId = request.queryParams("batchId");
        if (batchId == null) {
            //TODO place holder for now
            batchId = "-1";
        }

        // Dispatch request to OTP and store request/response summary if user elected to store trip history.
        long tripStorageStartTime = DateTimeUtils.currentTimeMillis();

        Auth0Connection.checkUser(request);
        RequestingUser requestingUser = Auth0Connection.getUserFromRequest(request);
        // A requesting user (Otp or third party user) is required to proceed.
        if (requestingUser.otpUser == null && requestingUser.apiUser == null) {
            return;
        }
        // TODO: Consider moving/replicating these checks (or a subset of) to before sending the request to OTP.
        // If a user id is provided, the assumption is that an Api user is making a plan request on behalf of an Otp user.
        String userId = request.queryParams(USER_ID_PARAM);
        OtpUser otpUser = null;
        if (requestingUser.otpUser != null && userId == null) {
            // Otp user making a trip request for self.
            otpUser = requestingUser.otpUser;
        } else if (requestingUser.apiUser != null) {
            // Api user making a trip request on behalf of an Otp user. In this case, the Otp user id must be provided
            // as a query parameter.
            otpUser = Persistence.otpUsers.getById(userId);
            if (otpUser == null && userId != null) {
                logMessageAndHalt(request, HttpStatus.NOT_FOUND_404, "The specified user id was not found.");
            } else if (!otpUser.canBeManagedBy(requestingUser)) {
                logMessageAndHalt(request,
                    HttpStatus.FORBIDDEN_403,
                    String.format("User: %s not authorized to make trip requests for user: %s",
                        requestingUser.apiUser.email,
                        otpUser.email));
            }
        }

        final boolean storeTripHistory = otpUser != null && otpUser.storeTripHistory;
        // only save trip details if the user has given consent and a response from OTP is provided
        if (!storeTripHistory) {
            LOG.debug("User does not want trip history stored");
        } else {
            OtpResponse otpResponse = otpDispatcherResponse.getResponse();
            if (otpResponse == null) {
                LOG.warn("OTP response is null, cannot save trip history for user!");
            } else {
                TripRequest tripRequest = new TripRequest(otpUser.id, batchId, request.queryParams("fromPlace"),
                    request.queryParams("toPlace"), request.queryString());
                // only save trip summary if the trip request was saved
                boolean tripRequestSaved = Persistence.tripRequests.create(tripRequest);
                if (tripRequestSaved) {
                    TripSummary tripSummary = new TripSummary(otpResponse.plan, otpResponse.error, tripRequest.id);
                    Persistence.tripSummaries.create(tripSummary);
                } else {
                    LOG.warn("Unable to save trip request, orphaned trip summary not saved");
                }
            }
        }
        LOG.debug("Trip storage added {} ms", DateTimeUtils.currentTimeMillis() - tripStorageStartTime);
    }
}
