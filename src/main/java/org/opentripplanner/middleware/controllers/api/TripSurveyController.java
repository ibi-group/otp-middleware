package org.opentripplanner.middleware.controllers.api;

import io.github.manusant.ss.SparkSwagger;
import io.github.manusant.ss.rest.Endpoint;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import spark.Request;
import spark.Response;

import static io.github.manusant.ss.descriptor.EndpointDescriptor.endpointPath;
import static io.github.manusant.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.utils.NotificationUtils.TRIP_SURVEY_ID;
import static org.opentripplanner.middleware.utils.NotificationUtils.TRIP_SURVEY_SUBDOMAIN;

public class TripSurveyController implements Endpoint {
    private final String ROOT_ROUTE;

    private static final String OPEN_PATH = "/open";

    public TripSurveyController(String apiPrefix) {
        this.ROOT_ROUTE = apiPrefix + "trip-survey";
    }

    /**
     * Register the API endpoint and GET resource that redirects to a survey form.
     */
    @Override
    public void bind(final SparkSwagger restApi) {
        restApi.endpoint(
            endpointPath(ROOT_ROUTE).withDescription("Interface for tracking opened trip surveys following a trip survey notification"),
            HttpUtils.NO_FILTER
        )
        .get(
            path(ROOT_ROUTE + OPEN_PATH)
                .withDescription("Generates a tracking survey link for a specified user, trip, notification ids.")
                .withQueryParam().withName("user_id").withRequired(true).withDescription("The id of the OtpUser that this notification applies to.").and()
                .withQueryParam().withName("trip_id").withRequired(true).withDescription("The id of the MonitoredTrip that this notification applies to.").and()
                .withQueryParam().withName("notification_id").withRequired(true).withDescription("The id of the notification that this notification applies to.").and(),
            TripSurveyController::processCall, JsonUtils::toJson
        );
    }

    /**
     * Check that the requested survey is valid (user, trip, and notifications point to existing data).
     */
    private static void checkParameters(Request req, Response res) {
        // TODO

    }

    /**
     * Mark notification as opened.
     */
    private static void updateNotificationState(Response res) {
        // TODO

    }

    public static String makeTripSurveyUrl(String subdomain, String surveyId, String userId, String tripId, String notificationId) {
        // Parameters have been checked before, so there shouldn't be a need to encode parameters.
        return String.format(
            "https://%s.typeform.com/to/%s#user_id=%s&trip_id=%s&notification_id=%s",
            subdomain,
            surveyId,
            userId,
            tripId,
            notificationId
        );
    }

    private static boolean processCall(Request req, Response res) {
        checkParameters(req, res);

        String surveyUrl = makeTripSurveyUrl(
            TRIP_SURVEY_SUBDOMAIN,
            TRIP_SURVEY_ID,
            req.queryParams("user_id"),
            req.queryParams("trip_id"),
            req.queryParams("notification_id")
        );

        // Update notification state
        updateNotificationState(res);

        // Redirect
        res.redirect(surveyUrl);

        return true;
    }
}
