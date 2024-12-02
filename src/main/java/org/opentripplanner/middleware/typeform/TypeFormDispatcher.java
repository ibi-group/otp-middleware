package org.opentripplanner.middleware.typeform;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.util.Strings;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.utils.DateTimeUtils;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.eclipse.jetty.http.HttpMethod.DELETE;
import static org.eclipse.jetty.http.HttpMethod.GET;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * This job will analyze completed trips with deviations and send survey notifications about select trips.
 */
public class TypeFormDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(TypeFormDispatcher.class);

    public static final String TRIP_SURVEY_API_TOKEN = getConfigPropertyAsText("TRIP_SURVEY_API_TOKEN");

    public static final String TRIP_SURVEY_ID = getConfigPropertyAsText("TRIP_SURVEY_ID");

    private TypeFormDispatcher() {
        // Hide the default constructor because this class only has static methods.
    }

    public static HttpResponseValues apiRequest(HttpMethod method, String subPath, String queryParams, String topic) {
        if (checkSurveyIdAndToken()) {
            HttpResponseValues response = HttpUtils.httpRequestRawResponse(
                URI.create(String.format("https://api.typeform.com/forms/%s%s%s", TRIP_SURVEY_ID, subPath, queryParams)),
                30,
                method,
                Map.of("Authorization", String.format("Bearer %s", TRIP_SURVEY_API_TOKEN)),
                null
            );

            if (response.status != HttpStatus.OK_200) {
                LOG.warn("Error {}-ing {}: [{}] {}", method, topic, response.status, response.responseBody);
            }

            return response;
        }
        return null;
    }

    public static Responses downloadSurveyResponses(LocalDateTime day) {
        HttpResponseValues response = apiRequest(GET, "/responses", responsesParams(day), "survey responses");
        if (response != null && response.status == HttpStatus.OK_200) {
            try {
                return JsonUtils.getPOJOFromJSON(response.responseBody, Responses.class);
            } catch (JsonProcessingException e) {
                LOG.warn("Error parsing survey responses", e);
            }
        }

        return null;
    }

    public static String downloadSurveyHeaders() {
        HttpResponseValues response = apiRequest(GET, "", "", "survey headers");
        if (response != null && response.status == HttpStatus.OK_200) {
            try {
                Form form = JsonUtils.getPOJOFromJSON(response.responseBody, Form.class);
                return form.toCsvHeader();
            } catch (JsonProcessingException e) {
                LOG.warn("Error parsing survey headers", e);
            }
        }

        return null;
    }

    public static void deleteSurveyResponses(List<String> ids) {
        if (!ids.isEmpty()) {
            String idParam = String.format("?included_response_ids=%s", String.join(",", ids));
            apiRequest(DELETE, "/responses", idParam, "survey responses");
        }
    }

    /** Assembles the query params for retrieving TypeForm survey responses. */
    public static String responsesParams(LocalDateTime day) {
        ZonedDateTime zonedDay = day.atZone(DateTimeUtils.getOtpZoneId());
        // The page_size param needs to be passed. Without it, only up to 25 responses are returned by TypeForm.
        // TypeForm can return up to 1000 responses in one query, see
        // https://www.typeform.com/developers/responses/reference/retrieve-responses/.
        return String.format(
            "?page_size=1000&since=%d&until=%d",
            zonedDay.toEpochSecond(),
            zonedDay.plusDays(1).minusSeconds(1).toEpochSecond()
        );
    }

    public static boolean checkSurveyIdAndToken() {
        boolean idAndTokenPresent = !Strings.isBlank(TRIP_SURVEY_API_TOKEN) && !Strings.isBlank(TRIP_SURVEY_ID);
        if (!idAndTokenPresent) {
            LOG.warn("Survey ID or survey response API token was not provided.");
        }
        return idAndTokenPresent;
    }
}
