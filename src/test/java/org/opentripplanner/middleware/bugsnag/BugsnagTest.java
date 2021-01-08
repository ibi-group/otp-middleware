package org.opentripplanner.middleware.bugsnag;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.OtpMiddlewareTest;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.CommonTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BugsnagTest {

    private static BugsnagEvent BUGSNAG_EVENT = null;
    private static BugsnagEventRequest BUGSNAG_EVENT_REQUEST = null;

    @BeforeAll
    public static void setup() throws IOException, InterruptedException {
        OtpMiddlewareTest.setUp();
        createBugsnagObjects();
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
     * Create Bugsnag objects from static JSON representations.
     */
    private static void createBugsnagObjects() throws IOException {
        final String resourceFilePath = "bugsnag/";
        BUGSNAG_EVENT = CommonTestUtils.getTestResourceAsJSON(
            resourceFilePath + "bugsnagEvent.json",
            BugsnagEvent.class
        );
        BUGSNAG_EVENT_REQUEST = CommonTestUtils.getTestResourceAsJSON(
            resourceFilePath + "bugsnagEventRequest.json",
            BugsnagEventRequest.class
        );
    }

}
