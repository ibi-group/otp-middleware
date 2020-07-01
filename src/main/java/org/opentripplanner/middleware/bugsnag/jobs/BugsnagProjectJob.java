package org.opentripplanner.middleware.bugsnag.jobs;

import com.mongodb.BasicDBObject;
import org.opentripplanner.middleware.bugsnag.BugsnagDispatcher;
import org.opentripplanner.middleware.models.BugsnagProject;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.persistence.TypedPersistence;

import java.util.List;

/**
 * Synchronize the projects held locally with the projects on Bugsnag. This will prevent event information being
 * reported to the admin dashboard minus project information. Project information is not provided as part of an
 * event request so has to be obtained via the project API:
 *
 *  - https://api.bugsnag.com/organizations/<organization_id>/projects
 */
public class BugsnagProjectJob implements Runnable {

    private static TypedPersistence<BugsnagProject> bugsnagProjects = Persistence.bugsnagProjects;

    /** Request all projects configured for an organization from Bugsnag and update Mongo */
    public void run() {
        List<BugsnagProject> projects = BugsnagDispatcher.getProjects();
        if (projects == null) {
            return;
        }

        // Remove all Bugsnag projects
        bugsnagProjects.removeFiltered(new BasicDBObject());

        // Sync Bugsnag projects
        bugsnagProjects.createMany(projects);
    }
}
