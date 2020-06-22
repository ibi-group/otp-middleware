package org.opentripplanner.middleware;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.FileUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BugsnagTest extends OtpMiddlewareTest {

    private static BugsnagEvent BUGSNAG_EVENT = null;
    private static BugsnagEventRequest BUGSNAG_EVENT_REQUEST = null;

    @BeforeAll
    public static void setup() {
        stageResponses();
    }

    @Test
    public void canCreateBugsnagEvent() {
        Persistence.bugsnagEvents.create(BUGSNAG_EVENT);
        BugsnagEvent retrieved = Persistence.bugsnagEvents.getById(BUGSNAG_EVENT.id);
        assertEquals(BUGSNAG_EVENT.id, retrieved.id, "Found Bugsnag event.");

    }

    @Test
    public void canCreateBugsnagEventRequest() {
        Persistence.bugsnagEventRequests.create(BUGSNAG_EVENT_REQUEST);
        BugsnagEventRequest retrieved = Persistence.bugsnagEventRequests.getById(BUGSNAG_EVENT_REQUEST.id);
        assertEquals(BUGSNAG_EVENT_REQUEST.id, retrieved.id, "Found Bugsnag event request.");
    }

    @AfterAll
    public static void tearDown() {
        if (BUGSNAG_EVENT != null) {
            Persistence.bugsnagEvents.removeById(BUGSNAG_EVENT.id);
        }

        if (BUGSNAG_EVENT_REQUEST != null) {
            Persistence.bugsnagEventRequests.removeById(BUGSNAG_EVENT_REQUEST.id);
        }
    }

    /**
     * Get Bugsnag responses from file for creating a Bugsnag event and event request.
     */
    private static void stageResponses() {
        final String filePath = "src/test/resources/org/opentripplanner/middleware/";
        BUGSNAG_EVENT = FileUtils.getFileContentsAsJSON(filePath + "bugsnagEvent.json", BugsnagEvent.class);
        BUGSNAG_EVENT_REQUEST = FileUtils.getFileContentsAsJSON(filePath + "bugsnagEventRequest.json", BugsnagEventRequest.class);
    }

}
