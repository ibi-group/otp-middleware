package org.opentripplanner.middleware.tripmonitor;

import com.mongodb.client.model.Filters;
import org.opentripplanner.middleware.OtpMiddlewareMain;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTrip.SETTINGS_PATH;
import static org.opentripplanner.middleware.utils.I18nUtils.label;

public class TrustedCompanion {

    private TrustedCompanion() {
        throw new IllegalStateException("Utility class");
    }

    private static final String AWS_API_SERVER = ConfigUtils.getConfigPropertyAsText("AWS_API_SERVER");
    private static final String AWS_API_STAGE = ConfigUtils.getConfigPropertyAsText("AWS_API_STAGE");
    private static final String OTP_UI_URL = ConfigUtils.getConfigPropertyAsText("OTP_UI_URL");
    private static final String TRUSTED_COMPANION_CONFIRMATION_PAGE_URL = ConfigUtils.getConfigPropertyAsText("TRUSTED_COMPANION_CONFIRMATION_PAGE_URL");
    public static final String ACCEPT_KEY = "acceptKey";
    public static final String EMAIL_FIELD_NAME = "email";

    /** Note: This path is excluded from security checks, see {@link OtpMiddlewareMain#initializeHttpEndpoints()}. */
    public static final String ACCEPT_DEPENDENT_PATH = "api/secure/user/acceptdependent";

    /**
     * Accept a request from another user to be their dependent. This will include both companions and observers. If
     * successful redirect the user to the confirmation page, else redirect to the error page with related error.
     */
    public static OtpUser acceptDependent(Request request, Response response) {
        try {
            String acceptKey = getAcceptKeyFromRequest(request);
            OtpUser dependentUser = getUserFromAcceptKey(acceptKey);
            OtpUser relatedUser = getRelatedUserFromEmail(dependentUser, acceptKey);

            if (relatedUser != null) {
                Optional<RelatedUser> relatedUserToUpdate = dependentUser.relatedUsers
                    .stream()
                    .filter(related -> related.email.equals(relatedUser.email))
                    .findFirst();
                relatedUserToUpdate.ifPresent(value -> value.status = RelatedUser.RelatedUserStatus.CONFIRMED);

                // Maintain a list of dependents.
                relatedUser.dependents.add(dependentUser.id);
                Persistence.otpUsers.replace(relatedUser.id, relatedUser);
                // Update list of related users.
                Persistence.otpUsers.replace(dependentUser.id, dependentUser);

                // Redirect to confirmation page and provide dependent user information.
                response.redirect(TRUSTED_COMPANION_CONFIRMATION_PAGE_URL);
                return dependentUser;
            }
        } catch (IllegalArgumentException e) {
            response.redirect(
                String.format("%s?error=%s", TRUSTED_COMPANION_CONFIRMATION_PAGE_URL, Message.ACCEPT_DEPENDENT_ERROR.get(null))
            );
        }
        return null;
    }

    /**
     * Using the accept key, find the matching related user's email and from that return the related user.
     */
    private static OtpUser getRelatedUserFromEmail(OtpUser dependentUser, String acceptKey) {
        if (dependentUser == null || acceptKey == null) {
            return null;
        }
        Optional<RelatedUser> relatedUser = dependentUser.relatedUsers
            .stream()
            .filter(user -> user.acceptKey.equalsIgnoreCase(acceptKey))
            .findFirst();
        return relatedUser.map(user -> Persistence.otpUsers.getOneFiltered(eq(EMAIL_FIELD_NAME, user.email))).orElse(null);
    }

    /**
     * Extract the accept key from the request parameters.
     */
    private static String getAcceptKeyFromRequest(Request request) throws IllegalArgumentException {
        // Note: optional is true so a missing accept key will be handled here.
        String acceptKey = HttpUtils.getQueryParamFromRequest(request, ACCEPT_KEY, true);
        if (acceptKey == null || acceptKey.isEmpty()) {
            throw new IllegalArgumentException("Accept key not provided.");
        }
        return acceptKey;
    }

    /**
     * Retrieve the dependent user matching the accept key.
     */
    private static OtpUser getUserFromAcceptKey(String acceptKey) throws IllegalArgumentException {
        if (acceptKey == null) {
            return null;
        }
        OtpUser user = getUserForAcceptKey(acceptKey);
        if (user == null) {
            throw new IllegalArgumentException("Otp user unknown.");
        }
        return user;
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
            .filter(relatedUser -> relatedUser.acceptKey == null)
            .forEach(relatedUser -> {
                String acceptKey = UUID.randomUUID().toString();
                OtpUser userToReceiveEmail = Persistence.otpUsers.getOneFiltered(eq(EMAIL_FIELD_NAME, relatedUser.email));
                if (userToReceiveEmail != null && (isTest || sendAcceptDependentEmail(dependentUser, userToReceiveEmail, acceptKey))) {
                    relatedUser.acceptKey = acceptKey;
                }
            });

        // Preserve email sent status (by storing the accept key).
        Persistence.otpUsers.replace(dependentUser.id, dependentUser);
    }

    /**
     * Send 'accept dependent' email.
     */
    private static boolean sendAcceptDependentEmail(OtpUser dependentUser, OtpUser relatedUser, String acceptKey) {
        Locale locale = I18nUtils.getOtpUserLocale(relatedUser);

        String acceptDependentLinkLabel = Message.ACCEPT_DEPENDENT_EMAIL_LINK_TEXT.get(locale);
        String acceptDependentUrl = getAcceptDependentUrl(acceptKey);
        String addressee = (dependentUser.name != null) ? dependentUser.name : dependentUser.email;

        // A HashMap is needed instead of a Map for template data to be serialized to the template renderer.
        Map<String, Object> templateData = new HashMap<>(
            Map.of(
                "acceptDependentLinkAnchorLabel", acceptDependentLinkLabel,
                "acceptDependentLinkLabelAndUrl", label(acceptDependentLinkLabel, acceptDependentUrl, locale),
                "acceptDependentUrl", acceptDependentUrl,
                "emailFooter", Message.ACCEPT_DEPENDENT_EMAIL_FOOTER.get(locale),
                "emailGreeting", String.format(Message.ACCEPT_DEPENDENT_EMAIL_GREETING.get(locale), addressee),
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

    private static String getAcceptDependentUrl(String acceptKey) {
        return String.format("%s/%s%s", AWS_API_SERVER, AWS_API_STAGE, getAcceptDependentEndPoint(acceptKey));
    }

    public static String getAcceptDependentEndPoint(String acceptKey) {
        return String.format("/%s?%s=%s", ACCEPT_DEPENDENT_PATH, ACCEPT_KEY, acceptKey);
    }

    /**
     * @return the {@link OtpUser} found with a {@link RelatedUser#acceptKey} in {@link OtpUser#relatedUsers} that
     * matches the provided acceptKey.
     */
    private static OtpUser getUserForAcceptKey(String acceptKey) {
        return Persistence.otpUsers.getOneFiltered(Filters.elemMatch("relatedUsers", Filters.eq(ACCEPT_KEY, acceptKey)));
    }

    /**
     * If a dependent removes a related user, remove the dependent from the related user.
     */
    public static void ensureRelatedUserIntegrity(OtpUser updatedUser, OtpUser preExistingUser) {
        List<RelatedUser> difference = preExistingUser.relatedUsers
            .stream()
            .filter(relatedUser -> !updatedUser.relatedUsers.contains(relatedUser))
            .collect(Collectors.toList());
        for (RelatedUser relatedUser : difference) {
            OtpUser user = Persistence.otpUsers.getOneFiltered(eq(EMAIL_FIELD_NAME, relatedUser.email));
            if (user != null) {
                user.dependents.remove(updatedUser.id);
                Persistence.otpUsers.replace(user.id, user);
            }
        }
    }
}
