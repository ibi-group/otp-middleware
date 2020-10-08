package org.opentripplanner.middleware.controllers.api;

import com.beerboy.ss.ApiEndpoint;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import org.apache.commons.lang3.StringUtils;
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
    private static final String PHONE_PARAM = "phoneNumber";
    private static final String VERIFY_PATH = "verify_sms";
    public static final String OTP_USER_PATH = "secure/user";
    private static final String VERIFY_ROUTE_TEMPLATE = "/:%s/%s/:%s";

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
        LOG.info("Registering path {}/{}.", ROOT_ROUTE, VERIFY_PATH);

        // Add the api key route BEFORE the regular CRUD methods
        ApiEndpoint modifiedEndpoint = baseEndpoint
            .get(path(ROOT_ROUTE + String.format(VERIFY_ROUTE_TEMPLATE, ID_PARAM, VERIFY_PATH, PHONE_PARAM))
                    .withDescription("Request an SMS verification to be sent to an OtpUser's phone number.")
                    .withPathParam().withName(ID_PARAM).withRequired(true).withDescription("The id of the OtpUser.").and()
                    .withPathParam().withName(PHONE_PARAM).withRequired(true).withDescription("The phone number to validate, in E.164 format.").and()
                    .withResponseType(VerificationResult.class),
                this::sendVerificationText, JsonUtils::toJson
            )
            .post(path(String.format(VERIFY_ROUTE_TEMPLATE, ID_PARAM, VERIFY_PATH, CODE_PARAM))
                    .withDescription("Verify an OtpUser's phone number with a verification code.")
                    .withPathParam().withName(ID_PARAM).withRequired(true).withDescription("The id of the OtpUser.").and()
                    .withPathParam().withName(CODE_PARAM).withRequired(true).withDescription("The SMS verification code.").and()
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
     * Before sending a SMS request, this endpoint also saves the submitted phone number, marks it as not verified.
     */
    public VerificationResult sendVerificationText(Request req, Response res) {
        OtpUser otpUser = getEntityForId(req, res);
        // Get phone number from the path param.
        String phoneNumber = req.params(PHONE_PARAM);
        if (StringUtils.isBlank(phoneNumber)) {
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "A phone number must be provided.");
        }

        // Update OtpUser before submitting SMS request.
        otpUser.phoneNumber = phoneNumber;
        otpUser.isPhoneNumberVerified = false;
        Persistence.otpUsers.replace(otpUser.id, otpUser);
/*
        Verification verification = NotificationUtils.sendVerificationText(phoneNumber);
        if (verification == null) {
            logMessageAndHalt(req, HttpStatus.INTERNAL_SERVER_ERROR_500, "Unknown error sending verification text");
        }
        // Verification result will show "pending" status if verification text is successfully sent.
        return new VerificationResult(verification);
 */
        return null;
    }

    /**
     * HTTP endpoint for an {@link OtpUser} to post a verification code sent to their phone in order to verify their
     * phone number.
     * If the verification succeeds, this endpoint sets isPhoneNumberVerified to true.
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
        // If the check is successful, status will be "approved".
        VerificationResult verificationResult = new VerificationResult(check);
        if (verificationResult.status.equals("approved")) {
            // If the check is successful, update the OtpUser
            // (set isPhoneNumberVerified and notificationChannel).
            // TODO: Figure out adding a test for this update to the OtpUser record.
            otpUser.isPhoneNumberVerified = true;
            Persistence.otpUsers.replace(otpUser.id, otpUser);
        }

        return verificationResult;
    }
}
