package org.opentripplanner.middleware.tripmonitor;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import org.apache.logging.log4j.util.Strings;
import org.eclipse.jetty.http.HttpStatus;
import org.opentripplanner.middleware.OtpMiddlewareMain;
import org.opentripplanner.middleware.auth.Auth0Connection;
import org.opentripplanner.middleware.i18n.Message;
import org.opentripplanner.middleware.models.MobilityProfileLite;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.RelatedUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.ConfigUtils;
import org.opentripplanner.middleware.utils.HttpUtils;
import org.opentripplanner.middleware.utils.I18nUtils;
import org.opentripplanner.middleware.utils.NotificationUtils;
import spark.Request;
import spark.Response;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.opentripplanner.middleware.tripmonitor.jobs.CheckMonitoredTrip.SETTINGS_PATH;
import static org.opentripplanner.middleware.utils.I18nUtils.getLocaleFromString;
import static org.opentripplanner.middleware.utils.I18nUtils.label;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class TrustedCompanion {

    private TrustedCompanion() {
        throw new IllegalStateException("Utility class");
    }

    private static final String AWS_API_SERVER = ConfigUtils.getConfigPropertyAsText("AWS_API_SERVER");
    private static final String AWS_API_STAGE = ConfigUtils.getConfigPropertyAsText("AWS_API_STAGE");
    private static final String OTP_UI_URL = ConfigUtils.getConfigPropertyAsText("OTP_UI_URL");
    private static final String TRUSTED_COMPANION_CONFIRMATION_PAGE_URL =
        ConfigUtils.getConfigPropertyAsText("TRUSTED_COMPANION_CONFIRMATION_PAGE_URL");
    public static final String ACCEPT_KEY = "acceptKey";
    public static final String USER_LOCALE = "userLocale";
    public static final String EMAIL_FIELD_NAME = "email";
    public static final String DEPENDENT_USER_IDS = "dependentuserids";

    /** Note: This path is excluded from security checks, see {@link OtpMiddlewareMain#initializeHttpEndpoints()}. */
    public static final String ACCEPT_DEPENDENT_PATH = "api/secure/user/acceptdependent";

    /**
     * Accept a request from another user to be their dependent. This will include both companions and observers. If
     * successful redirect the user to the confirmation page, else redirect to the error page with related error.
     */
    public static OtpUser acceptDependent(Request request, Response response) {
        Locale locale = getUserLocaleFromRequest(request);
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
            response.redirect(String.format(
                "%s?%s",
                TRUSTED_COMPANION_CONFIRMATION_PAGE_URL,
                URLEncoder.encode(String.format("error=%s", Message.ACCEPT_DEPENDENT_ERROR.get(locale)), UTF_8)
            ));
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
        if (Strings.isBlank(acceptKey)) {
            throw new IllegalArgumentException("Accept key not provided.");
        }
        return acceptKey;
    }

    /**
     * Extract the user's language tag from the request and return the {@link Locale} from it.
     */
    private static Locale getUserLocaleFromRequest(Request request) throws IllegalArgumentException {
        // Note: optional is true so a missing locale will be handled here.
        String languageTag = HttpUtils.getQueryParamFromRequest(request, USER_LOCALE, true);
        return getLocaleFromString(languageTag);
    }

    /**
     * Retrieve the dependent user matching the accept key.
     */
    private static OtpUser getUserFromAcceptKey(String acceptKey) throws IllegalArgumentException {
        if (Strings.isBlank(acceptKey)) {
            return null;
        }
        OtpUser user = getUserForAcceptKey(acceptKey);
        if (user == null) {
            throw new IllegalArgumentException("OTP user unknown.");
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
        String acceptDependentUrl = getAcceptDependentUrl(acceptKey, locale);

        // A HashMap is needed instead of a Map for template data to be serialized to the template renderer.
        Map<String, Object> templateData = new HashMap<>(
            Map.of(
                "acceptDependentLinkAnchorLabel", acceptDependentLinkLabel,
                "acceptDependentLinkLabelAndUrl", label(acceptDependentLinkLabel, acceptDependentUrl, locale),
                "acceptDependentUrl", acceptDependentUrl,
                "emailFooter", Message.ACCEPT_DEPENDENT_EMAIL_FOOTER.get(locale),
                "emailGreeting", String.format(Message.ACCEPT_DEPENDENT_EMAIL_GREETING.get(locale), dependentUser.getDisplayedName()),
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

    private static String getAcceptDependentUrl(String acceptKey, Locale locale) {
        return String.format("%s/%s%s", AWS_API_SERVER, AWS_API_STAGE, getAcceptDependentEndPoint(acceptKey, locale));
    }

    public static String getAcceptDependentEndPoint(String acceptKey, Locale locale) {
        return String.format("/%s?%s=%s&%s=%s", ACCEPT_DEPENDENT_PATH, ACCEPT_KEY, acceptKey, USER_LOCALE, locale.toLanguageTag());
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
            removeDependent(updatedUser, relatedUser);
        }
    }

    /**
     * Remove the dependent reference from the related user.
     */
    public static void removeDependent(OtpUser dependent, RelatedUser relatedUser) {
        OtpUser user = Persistence.otpUsers.getOneFiltered(eq(EMAIL_FIELD_NAME, relatedUser.email));
        if (user != null) {
            user.dependents.remove(dependent.id);
            Persistence.otpUsers.replace(user.id, user);
        }
    }

    /**
     * Retrieve the mobility profile for a dependent providing the requesting user is a trusted companion.
     */
    public static List<MobilityProfileLite> getDependentMobilityProfile(Request request, Response response) {
        var relatedUser = Auth0Connection.getUserFromRequest(request).otpUser;

        if (isEmpty(relatedUser)) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Related user not provided or unknown.");
        }

        var dependentUserIds = HttpUtils.getQueryParamFromRequest(request, DEPENDENT_USER_IDS, false);
        if (isEmpty(dependentUserIds)) {
            logMessageAndHalt(request, HttpStatus.BAD_REQUEST_400, "Required list of dependent user ids not provided.");
        }

        var validDependentUserIds = getValidDependents(relatedUser, dependentUserIds);
        if (validDependentUserIds.isEmpty()) {
            logMessageAndHalt(
                request,
                HttpStatus.FORBIDDEN_403,
                "Related user is not a trusted companion of any provided dependents!"
            );
        }

        if (isNotEmpty(relatedUser) && !validDependentUserIds.isEmpty()) {
            List<MobilityProfileLite> profiles = new ArrayList<>();
            FindIterable<OtpUser> validDependentUsers = Persistence
                .otpUsers
                .getFiltered(Filters.in("_id", validDependentUserIds));
            validDependentUsers.forEach(user -> profiles.add(new MobilityProfileLite(user)));
            return profiles;
        }
        return Collections.emptyList();
    }

    /**
     * From the list of dependent user ids, extract all that have the related user as their trusted companion.
     */
    private static Set<String> getValidDependents(OtpUser relatedUser, String dependentUserIds) {
        // In case only one user id is provided with no comma.
        String[] userIds = dependentUserIds.contains(",")
            ? dependentUserIds.split(",")
            : new String[] { dependentUserIds };

        if (isEmpty(userIds)) {
            return Collections.emptySet();
        }

        return Arrays
            .stream(userIds)
            .filter(userId -> relatedUser.dependents.contains(userId))
            .collect(Collectors.toSet());
    }
}