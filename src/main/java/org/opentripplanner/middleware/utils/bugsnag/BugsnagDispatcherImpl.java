package org.opentripplanner.middleware.utils.bugsnag;

import org.apache.tomcat.util.codec.binary.Base64;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.bugsnag.response.Organization;
import org.opentripplanner.middleware.utils.bugsnag.response.Project;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.opentripplanner.middleware.spark.Main.getConfigPropertyAsText;

public class BugsnagDispatcherImpl implements BugsnagDispatcher {
    String BugsnagUser = getConfigPropertyAsText("BUGSNAG_USER");
    String BugsnagPassword = getConfigPropertyAsText("BUGSNAG_PASSWORD");

    public Organization getOrganization() {

        String auth = BugsnagUser + ":" + BugsnagPassword;
        byte[] basicAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.UTF_8));

        URI uri = HttpUtils.buildUri(BUGSNAG_URL, ORGANIZATION_END_POINT);
        Organization organization = HttpUtils.call(uri, Organization.class, basicAuth);
        return organization;
    }

    public List<Project> getProjects() {
        return null;
    }

    public List<Error> getAllProjectErrors(String projectName) {
        return null;
    }

}
