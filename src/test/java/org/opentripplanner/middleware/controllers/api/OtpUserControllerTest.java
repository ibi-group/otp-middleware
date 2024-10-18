package org.opentripplanner.middleware.controllers.api;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.RelatedUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.createAndAssignAuth0User;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.getMockHeaders;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeGetRequest;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeRequest;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedGet;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.testutils.PersistenceTestUtils.deleteOtpUser;

public class OtpUserControllerTest extends OtpMiddlewareTestEnvironment {
    private static final String INITIAL_PHONE_NUMBER = "+15555550222"; // Fake US 555 number.
    private static OtpUser otpUser;
    private static OtpUser relatedUserOne;
    private static OtpUser dependentUserOne;
    private static OtpUser relatedUserTwo;
    private static OtpUser dependentUserTwo;
    private static OtpUser relatedUserThree;
    private static OtpUser dependentUserThree;
    private static HashMap<String, String> relatedUserHeaders;
    public static final String ACCEPT_DEPENDENT_PATH = "api/secure/user/acceptdependent";

    @BeforeAll
    public static void setUp() throws Exception {
        assumeTrue(IS_END_TO_END);
        // Set the overall auth to disabled.
        setAuthDisabled(false);

        // Create a persisted OTP user.
        otpUser = new OtpUser();
        otpUser.email = ApiTestUtils.generateEmailAddress("test-otpusercont");
        otpUser.hasConsentedToTerms = true;
        otpUser.phoneNumber = INITIAL_PHONE_NUMBER;
        otpUser.isPhoneNumberVerified = true;
        otpUser.smsConsentDate = new Date();
        Persistence.otpUsers.create(otpUser);

        relatedUserOne = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("related-user-one"));
        dependentUserOne = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("dependent-one"));
        relatedUserTwo = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("related-user-two"));
        dependentUserTwo = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("dependent-two"));
        relatedUserThree = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("related-user-three"));
        dependentUserThree = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("dependent-three"));
        relatedUserHeaders = createAndAssignAuth0User(relatedUserOne);
    }

    @AfterAll
    public static void tearDown() {
        deleteOtpUser(
            otpUser,
            relatedUserOne,
            relatedUserTwo,
            relatedUserThree,
            dependentUserOne,
            dependentUserTwo,
            dependentUserThree
        );

        // Restore original isAuthDisabled state.
        restoreDefaultAuthDisabled();
    }

    /**
     * Check that if for some reason the verification SMS is not sent (invalid format or other error from Twilio),
     * the request is 400-bad request/500-error, and that the user phone number is unchanged.
     */
    @ParameterizedTest
    @MethodSource("createBadPhoneNumbers")
    void invalidNumbersShouldProduceBadRequest(String badNumber, int statusCode) throws Exception {
        setAuthDisabled(true);
        // 1. Request verification SMS.
        // The invalid number should fail the call.
        HttpResponseValues response = mockAuthenticatedGet(
            String.format("api/secure/user/%s/verify_sms/%s",
                otpUser.id,
                badNumber
            ),
            otpUser
        );
        assertEquals(statusCode, response.status);

        // 2. Fetch the newly-created user.
        // The phone number should not be updated.
        HttpResponseValues otpUserWithPhoneRequest = mockAuthenticatedGet(
            String.format("api/secure/user/%s", otpUser.id),
            otpUser
        );
        assertEquals(HttpStatus.OK_200, otpUserWithPhoneRequest.status);

        OtpUser otpUserWithPhone = JsonUtils.getPOJOFromJSON(otpUserWithPhoneRequest.responseBody, OtpUser.class);
        assertEquals(INITIAL_PHONE_NUMBER, otpUserWithPhone.phoneNumber);
        assertTrue(otpUserWithPhone.isPhoneNumberVerified);
        setAuthDisabled(false);
    }

    private static Stream<Arguments> createBadPhoneNumbers() {
        return Stream.of(
            Arguments.of("5555555", HttpStatus.BAD_REQUEST_400),
            Arguments.of("+15555550001", HttpStatus.INTERNAL_SERVER_ERROR_500)
        );
    }

    /**
     * Tests that phone numbers meet the E.164 format (e.g. +15555550123).
     */
    @ParameterizedTest
    @MethodSource("createPhoneNumberTestCases")
    void isPhoneNumberValidE164(String number, boolean isValid) {
        assertEquals(isValid, OtpUserController.isPhoneNumberValidE164(number));
    }

    private static Stream<Arguments> createPhoneNumberTestCases() {
        return Stream.of(
            Arguments.of("+15555550123", true),
            Arguments.of("+1 5555550123", false), // no spaces allowed.
            Arguments.of("(555) 555,0123", false),
            Arguments.of("555555", false)
        );
    }

    /**
     * smsConsentDate is not passed to/from the UI, so make sure that that field still gets persisted.
     */
    @Test
    void canPreserveSmsConsentDate() throws Exception {
        OtpUser u = new OtpUser();
        u.id = otpUser.id;
        u.email = otpUser.email;
        u.hasConsentedToTerms = true;
        u.phoneNumber = INITIAL_PHONE_NUMBER;
        u.isPhoneNumberVerified = true;
        u.smsConsentDate = null;

        makeRequest(
            String.format("api/secure/user/%s", otpUser.id),
            JsonUtils.toJson(u),
            getMockHeaders(otpUser),
            HttpMethod.PUT
        );

        OtpUser updatedUser = Persistence.otpUsers.getById(otpUser.id);
        Assertions.assertEquals(otpUser.smsConsentDate, updatedUser.smsConsentDate);
    }

    @Test
    void canAcceptDependentRequest() {
        dependentUserOne.relatedUsers.add(new RelatedUser(relatedUserOne.id, relatedUserOne.email, RelatedUser.RelatedUserStatus.PENDING));
        Persistence.otpUsers.replace(dependentUserOne.id, dependentUserOne);

        String path = String.format("%s?userId=%s", ACCEPT_DEPENDENT_PATH, dependentUserOne.id);
        makeGetRequest(path, relatedUserHeaders);

        relatedUserOne = Persistence.otpUsers.getById(relatedUserOne.id);
        assertTrue(relatedUserOne.dependents.contains(dependentUserOne.id));

        dependentUserOne = Persistence.otpUsers.getById(dependentUserOne.id);
        List<RelatedUser> relatedUsers = dependentUserOne.relatedUsers;
        relatedUsers
            .stream()
            .filter(user -> user.userId.equals(relatedUserOne.id))
            .forEach(user -> assertEquals(RelatedUser.RelatedUserStatus.CONFIRMED, user.status));
    }

    @Test
    void canInvalidateDependent() {
        relatedUserTwo.dependents.add(dependentUserTwo.id);
        Persistence.otpUsers.replace(relatedUserTwo.id, relatedUserTwo);
        dependentUserTwo.relatedUsers.add(new RelatedUser(relatedUserTwo.id, relatedUserTwo.email, RelatedUser.RelatedUserStatus.CONFIRMED));
        Persistence.otpUsers.replace(dependentUserTwo.id, dependentUserTwo);
        relatedUserTwo.delete(false);
        dependentUserTwo = Persistence.otpUsers.getById(dependentUserTwo.id);
        RelatedUser relatedUser = dependentUserTwo.relatedUsers.get(0);
        assertEquals(RelatedUser.RelatedUserStatus.INVALID, relatedUser.status);
    }

    @Test
    void canRemoveRelatedUser() {
        relatedUserThree.dependents.add(dependentUserThree.id);
        Persistence.otpUsers.replace(relatedUserThree.id, relatedUserThree);
        dependentUserThree.relatedUsers.add(new RelatedUser(relatedUserThree.id, relatedUserThree.email, RelatedUser.RelatedUserStatus.CONFIRMED));
        Persistence.otpUsers.replace(dependentUserThree.id, dependentUserThree);
        dependentUserThree.delete(false);
        relatedUserThree = Persistence.otpUsers.getById(relatedUserThree.id);
        assertFalse(relatedUserThree.dependents.contains(dependentUserThree.id));
    }
}
