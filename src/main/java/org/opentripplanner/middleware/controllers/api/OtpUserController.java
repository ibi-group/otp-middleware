package org.opentripplanner.middleware.controllers.api;

import com.beerboy.ss.ApiEndpoint;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import org.eclipse.jetty.http.HttpStatus;
import com.mongodb.client.model.Filters;
import org.opentripplanner.middleware.auth.Auth0UserProfile;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.utils.JsonUtils;
import org.opentripplanner.middleware.utils.NotificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import static com.beerboy.ss.descriptor.MethodDescriptor.path;
import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

/**
 * Implementation of the {@link AbstractUserController} for {@link OtpUser}. This controller also contains
 * OtpUser-specific endpoints for verifying the user's phone number by SMS.
 */
public class OtpUserController extends AbstractUserController<OtpUser> {
    private static final Logger LOG = LoggerFactory.getLogger(OtpUserController.class);

    private static final String CODE_PARAM = "code";
    protected static final String CODE_PATH = "/:" + CODE_PARAM;
    private static final String VERIFY_ROUTE = "/verify_sms";
    public static final String OTP_USER_PATH = "secure/user";

    public OtpUserController(String apiPrefix) {
        super(apiPrefix, Persistence.otpUsers, OTP_USER_PATH);
    }

    @Override
    OtpUser preCreateHook(OtpUser user, Request req) {
        // Check API key and assign user to appropriate third-party application. Note: this is only relevant for
        // instances of otp-middleware running behind API Gateway.
        String apiKey = req.headers("x-api-key");
        ApiUser apiUser = Persistence.apiUsers.getOneFiltered(Filters.eq("apiKeys.value", apiKey));
        if (apiUser != null) {
            // If API user found, assign to new OTP user.
            user.applicationId = apiUser.id;
        } else {
            // If API user not found, report to Bugsnag for further investigation.
            BugsnagReporter.reportErrorToBugsnag(
                "OTP user created with API key that is not linked to any API user",
                apiKey,
                new IllegalArgumentException("API key not linked to API user.")
            );
        }
        return super.preCreateHook(user, req);
    }

    @Override
    protected void buildEndpoint(ApiEndpoint baseEndpoint) {
        LOG.info("Registering path {}.", ROOT_ROUTE + "verify");

        // Add the api key route BEFORE the regular CRUD methods
        ApiEndpoint modifiedEndpoint = baseEndpoint
            .get(path(ROOT_ROUTE + ID_PATH + VERIFY_ROUTE)
                    .withDescription("Request an SMS verification to be sent to an OtpUser's phone number.")
                    .withPathParam().withName(ID_PARAM).withDescription("The id of the OtpUser.").and()
                    .withResponseType(VerificationResult.class),
                this::sendVerificationText, JsonUtils::toJson
            )
            .post(path(ID_PATH + VERIFY_ROUTE + CODE_PATH)
                    .withDescription("Verify an OtpUser's phone number with a verification code.")
                    .withPathParam().withName(ID_PARAM).withDescription("The id of the OtpUser.").and()
                    .withPathParam().withName(CODE_PARAM).withDescription("The SMS verification code.").and()
                    .withResponseType(VerificationResult.class),
                this::verifyPhoneWithCode, JsonUtils::toJson
            );
        // Add the regular CRUD methods after defining the controller-specific routes.
        super.buildEndpoint(modifiedEndpoint);
    }

    @Override
    protected OtpUser getUserProfile(Auth0UserProfile profile) {
        return profile.otpUser;
    }

    /**
     * HTTP endpoint to send an SMS text to an {@link OtpUser}'s phone number with a verification code. This is used
     * during user signup (or if a user wishes to change their notification preferences to use a new un-verified phone
     * number).
     */
    public VerificationResult sendVerificationText(Request req, Response res) {
        OtpUser otpUser = getEntityForId(req, res);
        if (otpUser.phoneNumber == null) {
            logMessageAndHalt(req, HttpStatus.NOT_FOUND_404, "User must have valid phone number to verify.");
        }
        Verification verification = NotificationUtils.sendVerificationText(otpUser.phoneNumber);
        if (verification == null) {
            logMessageAndHalt(req, HttpStatus.INTERNAL_SERVER_ERROR_500, "Unknown error sending verification text");
        }
        // Verification result will show "pending" status if verification text is successfully sent.
        return new VerificationResult(verification);
    }

    /**
     * HTTP endpoint for an {@link OtpUser} to post a verification code sent to their phone in order to verify their
     * phone number.
     */
    public VerificationResult verifyPhoneWithCode(Request req, Response res) {
        OtpUser otpUser = getEntityForId(req, res);
        // Get verification code from path param.
        String code = req.params(CODE_PARAM);
        if (code == null) {
            logMessageAndHalt(req, 400, "Missing code from verify request.");
        }
        if (otpUser.phoneNumber == null) {
            logMessageAndHalt(req, 404, "User must have valid phone number for SMS verification.");
        }
        // Check verification code with SMS service.
        VerificationCheck check = NotificationUtils.checkSmsVerificationCode(otpUser.phoneNumber, code);
        if (check == null) {
            logMessageAndHalt(
                req,
                HttpStatus.INTERNAL_SERVER_ERROR_500,
                "Unknown error encountered while checking SMS verification code"
            );
        }
        // If the check is successful, status will be "approved"
        return new VerificationResult(check);
    }
}
