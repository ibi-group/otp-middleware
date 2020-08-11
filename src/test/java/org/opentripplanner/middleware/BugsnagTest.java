package org.opentripplanner.middleware;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.BugsnagEvent;
import org.opentripplanner.middleware.models.BugsnagEventRequest;
import org.opentripplanner.middleware.models.BugsnagProject;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.FileUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BugsnagTest extends OtpMiddlewareTest {

    private static BugsnagEvent BUGSNAG_EVENT = null;
    private static BugsnagEventRequest BUGSNAG_EVENT_REQUEST = null;
    private static BugsnagProject BUGSNAG_PROJECT = null;

    @BeforeAll
    public static void setup() throws IOException {
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

    @Test
    public void canCreateBugsnagProject() {
        Persistence.bugsnagProjects.create(BUGSNAG_PROJECT);
        BugsnagProject retrieved = Persistence.bugsnagProjects.getById(BUGSNAG_PROJECT.id);
        assertEquals(BUGSNAG_PROJECT.id, retrieved.id, "Found Bugsnag project.");
    }

    @AfterAll
    public static void tearDown() {
        if (BUGSNAG_EVENT != null) {
            Persistence.bugsnagEvents.removeById(BUGSNAG_EVENT.id);
        }

        if (BUGSNAG_EVENT_REQUEST != null) {
            Persistence.bugsnagEventRequests.removeById(BUGSNAG_EVENT_REQUEST.id);
        }

        if (BUGSNAG_PROJECT != null) {
            Persistence.bugsnagProjects.removeById(BUGSNAG_PROJECT.id);
        }
    }

    /**
     * Create Bugsnag objects from static JSON representations.
     */
    private static void createBugsnagObjects() throws IOException {
        final String resourceFilePath = "bugsnag/";
        BUGSNAG_EVENT = TestUtils.getResourceFileContentsAsJSON(resourceFilePath + "bugsnagEvent.json", BugsnagEvent.class);
        BUGSNAG_EVENT_REQUEST = TestUtils.getResourceFileContentsAsJSON(resourceFilePath + "bugsnagEventRequest.json", BugsnagEventRequest.class);
        BUGSNAG_PROJECT = TestUtils.getResourceFileContentsAsJSON(resourceFilePath + "bugsnagProject.json", BugsnagProject.class);
    }

}
