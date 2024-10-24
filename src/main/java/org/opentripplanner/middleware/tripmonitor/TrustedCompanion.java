package org.opentripplanner.middleware.tripmonitor;

import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.auth.RequestingUser;
import org.opentripplanner.middleware.i18n.Message;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.RelatedUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.I18nUtils;
import org.opentripplanner.middleware.utils.NotificationUtils;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.opentripplanner.middleware.controllers.api.ApiController.USER_ID_PARAM;
import static org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTrip.SETTINGS_PATH;
import static org.opentripplanner.middleware.utils.I18nUtils.label;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class TrustedCompanion {

    private TrustedCompanion() {
        throw new IllegalStateException("Utility class");
    }

    private static final String OTP_UI_URL = ConfigUtils.getConfigPropertyAsText("OTP_UI_URL");

    public static final String ACCEPT_DEPENDENT_PATH = "api/secure/user/acceptdependent";

    /**
     * Accept a request from another user to be their dependent. This will include both companions and observers.
     */
    public static OtpUser acceptDependent(Request request, Response response) {
        RequestingUser requestingUser = Auth0Connection.getUserFromRequest(request);
        OtpUser relatedUser = requestingUser.otpUser;
        if (relatedUser == null) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Otp user unknown.");
            return null;
        }

        String dependentUserId = HttpUtils.getQueryParamFromRequest(request, USER_ID_PARAM, false);
        if (dependentUserId.isEmpty()) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Dependent user id not provided.");
            return null;
        }

        OtpUser dependentUser = Persistence.otpUsers.getById(dependentUserId);
        if (dependentUser == null) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Dependent user unknown!");
            return null;
        }

        boolean isRelated = dependentUser.relatedUsers
            .stream()
            .filter(related -> related.userId.equals(relatedUser.id))
            // Update related user status. This assumes a related user with "pending" status was previously added.
            .peek(related -> related.status = RelatedUser.RelatedUserStatus.CONFIRMED)
            .findFirst()
            .isPresent();

        if (isRelated) {
            // Maintain a list of dependents.
            relatedUser.dependents.add(dependentUserId);
            Persistence.otpUsers.replace(relatedUser.id, relatedUser);
            // Update list of related users.
            Persistence.otpUsers.replace(dependentUser.id, dependentUser);
        } else {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Dependent did not request user to be related!");
            return null;
        }

        // TODO: Not sure what is required in the response. For now, returning the updated related user.
        return relatedUser;
    }

    public static void manageAcceptDependentEmail(OtpUser dependentUser) {
        manageAcceptDependentEmail(dependentUser, false);
    }

    /**
     * When creating or updating an OTP user, extract a list of newly defined dependents and send an 'accept dependent'
     * email to each. Then update which dependents have been sent an email so subsequent updates do not trigger
     * additional emails.
     */
    public static void manageAcceptDependentEmail(OtpUser dependentUser, boolean isTest) {
        if (dependentUser.relatedUsers.isEmpty()) {
            // No related users defined by dependent.
            return;
        }

        dependentUser.relatedUsers
            .stream()
            .filter(relatedUser -> !relatedUser.acceptDependentEmailSent)
            .forEach(relatedUser -> {
                OtpUser userToReceiveEmail = Persistence.otpUsers.getById(relatedUser.userId);
                if (userToReceiveEmail != null && (isTest || sendAcceptDependentEmail(dependentUser, userToReceiveEmail))) {
                    relatedUser.acceptDependentEmailSent = true;
                }
            });

        // Preserve email sent status.
        Persistence.otpUsers.replace(dependentUser.id, dependentUser);
    }

    /**
     * Send 'accept dependent' email.
     */
    private static boolean sendAcceptDependentEmail(OtpUser dependentUser, OtpUser relatedUser) {
        Locale locale = I18nUtils.getOtpUserLocale(relatedUser);

        String acceptDependentLinkLabel = Message.ACCEPT_DEPENDENT_EMAIL_LINK_TEXT.get(locale);
        String acceptDependentUrl = getAcceptDependentUrl(dependentUser);

        // A HashMap is needed instead of a Map for template data to be serialized to the template renderer.
        Map<String, Object> templateData = new HashMap<>(
            Map.of(
                "acceptDependentLinkAnchorLabel", acceptDependentLinkLabel,
                "acceptDependentLinkLabelAndUrl", label(acceptDependentLinkLabel, acceptDependentUrl, locale),
                "acceptDependentUrl", getAcceptDependentUrl(dependentUser),
                "emailFooter", Message.ACCEPT_DEPENDENT_EMAIL_FOOTER.get(locale),
                // TODO: The user's email address isn't very personal, but that is all I have to work with! Suggetions?
                "emailGreeting", String.format("%s%s", dependentUser.email, Message.ACCEPT_DEPENDENT_EMAIL_GREETING.get(locale)),
                // TODO: This is required in the `OtpUserContainer.ftl` template. Not sure what to provide. Suggestions?
                "manageLinkUrl", String.format("%s%s", OTP_UI_URL, SETTINGS_PATH),
                "manageLinkText", Message.ACCEPT_DEPENDENT_EMAIL_MANAGE.get(locale)
            )
        );

        return NotificationUtils.sendEmail(
            relatedUser,
            Message.ACCEPT_DEPENDENT_EMAIL_SUBJECT.get(locale),
            "AcceptDependentText.ftl",
            "AcceptDependentHtml.ftl",
            templateData
        );
    }

    private static String getAcceptDependentUrl(OtpUser dependentUser) {
        // TODO: Is OTP_UI_URL the correct base URL to user here? I'm not sure.
        return String.format("%s%s?userId=%s", OTP_UI_URL, ACCEPT_DEPENDENT_PATH, dependentUser.id);
    }
}
