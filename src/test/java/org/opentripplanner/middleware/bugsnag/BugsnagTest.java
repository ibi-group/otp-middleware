package org.opentripplanner.middleware.bugsnag;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.mongodb.client.model.Filters;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventHandlingJob;
import org.opentripplanner.middleware.bugsnag.jobs.BugsnagEventRequestJob;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.CommonTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.opentripplanner.middleware.bugsnag.BugsnagDispatcher.TEST_BUGSNAG_DOMAIN;
import static org.opentripplanner.middleware.bugsnag.BugsnagDispatcher.TEST_BUGSNAG_PORT;
import static org.opentripplanner.middleware.bugsnag.BugsnagJobs.getHoursRoundedToWholeDaySinceDate;
import static org.opentripplanner.middleware.bugsnag.BugsnagJobs.getHoursSinceLastRequest;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeRequest;

public class BugsnagTest extends OtpMiddlewareTestEnvironment {

    private static WireMockServer wireMockServer;

    @BeforeAll
    public static void setup() throws IOException {
        // Set Bugsnage dispatcher URL to test domain used by wiremock.
        BugsnagDispatcher.setBaseUsersUrl(TEST_BUGSNAG_DOMAIN);
        // This sets up a mock server that accepts requests and sends predefined responses to mock the Bugsnag API.
        wireMockServer = new WireMockServer(
            options()
                .port(TEST_BUGSNAG_PORT)
                .usingFilesUnderDirectory("src/test/resources/org/opentripplanner/middleware/bugsnag-mock-responses/")
        );
        wireMockServer.start();
    }

    @AfterAll
    public static void tearDown() {
        wireMockServer.stop();
    }

    /**
     * Test to confirm an event data requests can be correctly processed and saved.
     */
    @Test
    public void canMockResponseFromBugsnag() {
        String projectId = UUID.randomUUID().toString();
        // create wiremock stub for get event data request
        wireMockServer.stubFor(
            post(urlMatching(String.format("/projects/%s/event_data_requests", projectId)))
                .willReturn(
                    aResponse()
                        .withBodyFile("createEventDataRequest.json")
                )
        );
        BugsnagEventRequestJob.triggerEventDataRequestForProject(projectId, 14);
        BugsnagEventRequest bugsnagEventRequest =
            Persistence.bugsnagEventRequests.getOneFiltered(Filters.eq("projectId", projectId));
        assertNotNull(bugsnagEventRequest);
        bugsnagEventRequest.delete();
    }

    /**
     * Provide a 'pending' event request which when refreshed (using CompletedEventDataRequest.son) is updated to
     * 'completed'. Using the url parameter from the refreshed event request, process the event data response
     * (eventDataResponse.json) and save the Bugsnag event. Confirm that the refreshed event request status has been
     * updated to 'completed' and that the Bugsnag event has been saved.
     *
     * Note: the CompletedEventDataRequest.son contains a hardcoded reference to the wiremock URL:
     * http://localhost:8089/bugsnag-event-data-requests/611a9123b6e99d4fcec2dc5c
     */
    @Test
    public void processCompletedEventRequest() {
        String projectId = "5ee8f4026c0a34000e1b1394"; // Must match the project id value in eventDataResponse.json
        String eventDataRequestId = "611a9123b6e99d4fcec2dc5c"; // Must match the id value in createEventDataRequest.json
        BugsnagEventRequest bugsnagEventRequest =
            createBugsnagEventRequest(
                projectId,
                eventDataRequestId,
                "pending",
                getDateInPast(1)
            );
        // create wiremock stub for the refresh event data request.
        wireMockServer.stubFor(
            get(urlMatching(String.format("/projects/%s/event_data_requests/%s", projectId, eventDataRequestId)))
                .willReturn(
                    aResponse()
                        .withBodyFile("completedEventDataRequest.json")
                )
        );
        // create wiremock stub for the event data response.
        wireMockServer.stubFor(
            get(urlMatching(String.format("/bugsnag-event-data-requests/%s", eventDataRequestId)))
                .willReturn(
                    aResponse()
                        .withBodyFile("eventDataResponse.json")
                )
        );
        BugsnagEventHandlingJob bugsnagEventHandlingJob = new BugsnagEventHandlingJob();
        bugsnagEventHandlingJob.refreshEventRequest(bugsnagEventRequest);
        BugsnagEventRequest updatedBugsnagEventRequest = Persistence.bugsnagEventRequests.getById(bugsnagEventRequest.id);
        assertEquals(updatedBugsnagEventRequest.status, "COMPLETED");
        bugsnagEventRequest.delete();
        BugsnagEvent bugsnagEvent = Persistence.bugsnagEvents.getOneFiltered(Filters.eq("projectId", projectId));
        assertNotNull(bugsnagEvent);
        Persistence.bugsnagEvents.removeById(bugsnagEvent.id);
    }

    /**
     * Confirm that when an event request has expired, the reporting window (days in past) is recalculated and a new
     * event request replaces the previous expired one.
     */
    @Test
    public void processExpiredEventRequest() {
        // Must match the project id value in eventDataResponse.json
        String projectId = "79gg84750000d122";
        // Must match the id value in expiredEventDataRequest.json
        String expiredEventDataRequestId = "611a9123b6e99d4fcec2dc5c";
        // Must match the id value in replacedEventDataRequest.json
        String replacedEventDataRequestId = "5948fhf734h362";
        BugsnagEventRequest bugsnagEventRequest =
            createBugsnagEventRequest(
                projectId,
                expiredEventDataRequestId,
                "pending",
                getDateInPast(2)
            );
        // create wiremock stub for the refresh event data request.
        wireMockServer.stubFor(
            get(urlMatching(String.format("/projects/%s/event_data_requests/%s", projectId, expiredEventDataRequestId)))
                .willReturn(
                    aResponse()
                        .withBodyFile("expiredEventDataRequest.json")
                )
        );
        // create wiremock stub for new event data request
        wireMockServer.stubFor(
            post(urlMatching(String.format("/projects/%s/event_data_requests", projectId)))
                .willReturn(
                    aResponse()
                        .withBodyFile("replacedEventDataRequest.json")
                )
        );
        BugsnagEventHandlingJob bugsnagEventHandlingJob = new BugsnagEventHandlingJob();
        bugsnagEventHandlingJob.refreshEventRequest(bugsnagEventRequest);
        BugsnagEventRequest removedBugsnagEventRequest =
            Persistence.bugsnagEventRequests.getOneFiltered(
                Filters.and(
                    Filters.eq("projectId", projectId),
                    Filters.eq("eventDataRequestId", expiredEventDataRequestId)
                )
            );
        assertNull(removedBugsnagEventRequest);

        BugsnagEventRequest newBugsnagEventRequest =
            Persistence.bugsnagEventRequests.getOneFiltered(
                Filters.and(
                    Filters.eq("projectId", projectId),
                    Filters.eq("eventDataRequestId", replacedEventDataRequestId)
                )
            );
        assertNotNull(newBugsnagEventRequest);
        // Rounding hour to nearest whole day bumps this by a day from the original 2 to 3 days.
        assertEquals(3, newBugsnagEventRequest.daysInPast);
        assertEquals("PREPARING", newBugsnagEventRequest.status);
        newBugsnagEventRequest.delete();
    }

    /**
     * Confirm that the contents of a Bugsnag webhook delivery can be parsed and saved as a Bugsnag event.
     */
    @Test
    public void processBugsnagWebhookDelivery() throws IOException {
        String projectId = "56b9ca7f17025f8756f69054"; // Must match the project id value in bugsnagWebhookDelivery.json
        String payload = CommonTestUtils.getTestResourceAsString("bugsnag/bugsnagWebhookDelivery.json");
        makeRequest("/api/bugsnagwebhook", payload, null, HttpMethod.POST);
        BugsnagEvent bugsnagEvent = Persistence.bugsnagEvents.getOneFiltered(Filters.eq("projectId", projectId));
        assertNotNull(bugsnagEvent);
        Persistence.bugsnagEvents.removeById(bugsnagEvent.id);
    }

    /**
     * Make sure the maximum reporting window is not exceeded. If the last completed event request is older than the
     * maximum reporting window, use the maximum reporting window.
     */
    @Test
    public void maximumReportingWindowNotExceeded() {
        String projectId = UUID.randomUUID().toString();
        BugsnagEventRequest bugsnagEventRequest =
            createBugsnagEventRequest(
                projectId,
                "completed",
                getDateInPast(BugsnagDispatcher.BUGSNAG_REPORTING_WINDOW_IN_DAYS + 1)
            );
        assertEquals(BugsnagDispatcher.BUGSNAG_REPORTING_WINDOW_IN_DAYS * 24, getHoursSinceLastRequest(projectId));
        bugsnagEventRequest.delete();
    }

    /**
     * Make sure the maximum reporting window is used if no event requests have completed and the last incomplete
     * has expired.
     */
    @Test
    public void maximumReportingWindowUsed() {
        String projectId = UUID.randomUUID().toString();
        BugsnagEventRequest bugsnagEventRequest = createBugsnagEventRequest(projectId, "expired", getDateInPast(10));
        assertEquals(BugsnagDispatcher.BUGSNAG_REPORTING_WINDOW_IN_DAYS * 24, getHoursSinceLastRequest(projectId));
        bugsnagEventRequest.delete();
    }

    /**
     * Make sure the last incomplete event request is used if not expired.
     */
    @Test
    public void lastIncompleteIsUsed() {
        String projectId = UUID.randomUUID().toString();
        Date date = getDateInPast(10);
        BugsnagEventRequest bugsnagEventRequest = createBugsnagEventRequest( projectId,  "preparing", date);
        assertEquals(getHoursRoundedToWholeDaySinceDate(date.getTime()), getHoursSinceLastRequest(projectId));
        bugsnagEventRequest.delete();
    }

    /**
     * Make sure the last incomplete event request that has not expired, is used over a previously completed event
     * request.
     */
    @Test
    public void lastIncompleteHasPrecedence() {
        String projectId = UUID.randomUUID().toString();
        Date incompleteDate = getDateInPast(9);
        BugsnagEventRequest incomplete = createBugsnagEventRequest(projectId, "preparing", incompleteDate);
        BugsnagEventRequest complete = createBugsnagEventRequest(projectId, "completed", getDateInPast(10));
        assertEquals(getHoursRoundedToWholeDaySinceDate(incompleteDate.getTime()), getHoursSinceLastRequest(projectId));
        incomplete.delete();
        complete.delete();
    }

    /**
     * Make sure the last complete event request is used over a newer, expired and incomplete event request.
     */
    @Test
    public void lastCompleteHasPrecedence() {
        String projectId = UUID.randomUUID().toString();
        BugsnagEventRequest incomplete = createBugsnagEventRequest(projectId, "expired", getDateInPast(9));
        Date completeDate = getDateInPast(10);
        BugsnagEventRequest complete = createBugsnagEventRequest(projectId, "completed", completeDate);
        assertEquals(getHoursRoundedToWholeDaySinceDate(completeDate.getTime()), getHoursSinceLastRequest(projectId));
        incomplete.delete();
        complete.delete();
    }

    /**
     * Create a date in the passed based on the days provided. Fix the hours, minutes and seconds for consistency.
     */
    private Date getDateInPast(int minusDays) {
        return Date.from(
            LocalDate.now()
                .minusDays(minusDays)
                .atTime(10, 0, 0)
                .toInstant(ZoneOffset.UTC)
        );
    }

    private BugsnagEventRequest createBugsnagEventRequest(String projectId, String status, Date dateCreated) {
        return createBugsnagEventRequest(projectId, null, status, dateCreated);
    }

    private BugsnagEventRequest createBugsnagEventRequest(
        String projectId,
        String eventDataRequestId,
        String status,
        Date dateCreated
    ) {
        BugsnagEventRequest bugsnagEventRequest = new BugsnagEventRequest();
        bugsnagEventRequest.projectId = projectId;
        bugsnagEventRequest.eventDataRequestId = eventDataRequestId;
        bugsnagEventRequest.status = status;
        bugsnagEventRequest.dateCreated = dateCreated;
        Persistence.bugsnagEventRequests.create(bugsnagEventRequest);
        return bugsnagEventRequest;
    }
}
