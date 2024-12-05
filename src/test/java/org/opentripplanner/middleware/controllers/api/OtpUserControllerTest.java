package org.opentripplanner.middleware.controllers.api;

import com.auth0.json.mgmt.users.User;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.middleware.models.MobilityProfile;
import org.opentripplanner.middleware.models.MobilityProfileLite;
import org.opentripplanner.middleware.models.MonitoredTrip;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.RelatedUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;
import org.opentripplanner.middleware.tripmonitor.TrustedCompanion;
import org.opentripplanner.middleware.utils.HttpResponseValues;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.restoreDefaultAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.auth.Auth0Users.createAuth0UserForEmail;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.TEMP_AUTH0_USER_PASSWORD;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.getMockHeaders;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeGetRequest;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.makeRequest;
import static org.opentripplanner.middleware.testutils.ApiTestUtils.mockAuthenticatedGet;
import static org.opentripplanner.middleware.testutils.PersistenceTestUtils.deleteOtpUser;
import static org.opentripplanner.middleware.tripmonitor.TrustedCompanion.DEPENDENT_USER_IDS;

public class OtpUserControllerTest extends OtpMiddlewareTestEnvironment {
    private static final String INITIAL_PHONE_NUMBER = "+15555550222"; // Fake US 555 number.
    private static OtpUser otpUser;
    private static OtpUser relatedUserOne;
    private static OtpUser dependentUserOne;
    private static OtpUser relatedUserTwo;
    private static OtpUser dependentUserTwo;
    private static OtpUser relatedUserThree;
    private static OtpUser dependentUserThree;
    private static OtpUser relatedUserFour;
    private static OtpUser dependentUserFour;
    private static final String nickname = "my-trusted-companion";

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

        relatedUserFour = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("related-user-four"));

        User auth0User = createAuth0UserForEmail(relatedUserFour.email, TEMP_AUTH0_USER_PASSWORD);
        relatedUserFour.auth0UserId = auth0User.getId();
        Persistence.otpUsers.replace(relatedUserFour.id, relatedUserFour);

        dependentUserFour = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("dependent-four"));
    }

    @AfterAll
    public static void tearDown() {
        deleteOtpUser(
            IS_END_TO_END,
            otpUser,
            relatedUserOne,
            relatedUserTwo,
            relatedUserThree,
            relatedUserFour,
            dependentUserOne,
            dependentUserTwo,
            dependentUserThree,
            dependentUserFour
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
        String acceptKey = UUID.randomUUID().toString();
        dependentUserOne.relatedUsers.add(new RelatedUser(
            relatedUserOne.email,
            RelatedUser.RelatedUserStatus.PENDING,
            nickname,
            acceptKey
        ));
        Persistence.otpUsers.replace(dependentUserOne.id, dependentUserOne);

        Locale locale = new Locale("en", "GB");
        String path = TrustedCompanion.getAcceptDependentEndPoint(acceptKey, locale);
        makeGetRequest(path, null);

        relatedUserOne = Persistence.otpUsers.getById(relatedUserOne.id);
        assertTrue(relatedUserOne.dependents.contains(dependentUserOne.id));

        dependentUserOne = Persistence.otpUsers.getById(dependentUserOne.id);
        List<RelatedUser> relatedUsers = dependentUserOne.relatedUsers;
        relatedUsers
            .stream()
            .filter(user -> user.email.equals(relatedUserOne.email))
            .forEach(user -> assertEquals(RelatedUser.RelatedUserStatus.CONFIRMED, user.status));
    }

    @Test
    void canInvalidateDependentOnDelete() {
        relatedUserTwo.dependents.add(dependentUserTwo.id);
        Persistence.otpUsers.replace(relatedUserTwo.id, relatedUserTwo);
        dependentUserTwo.relatedUsers.add(new RelatedUser(
            relatedUserTwo.email,
            RelatedUser.RelatedUserStatus.CONFIRMED,
            nickname
        ));
        Persistence.otpUsers.replace(dependentUserTwo.id, dependentUserTwo);
        relatedUserTwo.delete(false);
        dependentUserTwo = Persistence.otpUsers.getById(dependentUserTwo.id);
        RelatedUser relatedUser = dependentUserTwo.relatedUsers.get(0);
        assertEquals(RelatedUser.RelatedUserStatus.INVALID, relatedUser.status);
    }

    @Test
    void canRemoveRelatedUserOnDelete() {
        relatedUserThree.dependents.add(dependentUserThree.id);
        Persistence.otpUsers.replace(relatedUserThree.id, relatedUserThree);
        dependentUserThree.relatedUsers.add(new RelatedUser(
            relatedUserThree.email,
            RelatedUser.RelatedUserStatus.CONFIRMED,
            nickname
        ));
        Persistence.otpUsers.replace(dependentUserThree.id, dependentUserThree);

        // Create a monitored trip with dependentUserThree as primary traveler, relatedUserThree as companion.
        MonitoredTrip trip = new MonitoredTrip();
        trip.id = UUID.randomUUID().toString();
        trip.primary = new MobilityProfileLite(dependentUserThree);
        trip.companion = new RelatedUser(relatedUserThree.email, RelatedUser.RelatedUserStatus.CONFIRMED, "nickname");
        Persistence.monitoredTrips.create(trip);

        dependentUserThree.delete(false);
        relatedUserThree = Persistence.otpUsers.getById(relatedUserThree.id);
        assertFalse(relatedUserThree.dependents.contains(dependentUserThree.id));

        // If a dependent user deletes their profile, delete them from any trip where they are a dependent.
        MonitoredTrip updatedTrip = Persistence.monitoredTrips.getById(trip.id);
        assertNull(updatedTrip.primary);
    }

    /**
     * Confirm that a user can be removed from a related users list, and importantly, the related user no longer lists
     * the removed dependent.
     */
    @Test
    void canRemoveUserFromRelatedUsersList() throws Exception {
        setAuthDisabled(true);

        createTrustedCompanionship(relatedUserFour, dependentUserFour);

        // Remove the first related user.
        dependentUserFour.relatedUsers.clear();

        // Add a new related user that should not be considered for integrity update.
        dependentUserFour.relatedUsers.add(new RelatedUser(
            relatedUserThree.email,
            RelatedUser.RelatedUserStatus.PENDING,
            nickname
        ));

        makeRequest(
            String.format("api/secure/user/%s", dependentUserFour.id),
            JsonUtils.toJson(dependentUserFour),
            getMockHeaders(dependentUserFour),
            HttpMethod.PUT
        );

        dependentUserFour = Persistence.otpUsers.getById(dependentUserFour.id);
        assertFalse(dependentUserFour.relatedUsers.stream().anyMatch(u -> u.email.equalsIgnoreCase(relatedUserFour.email)));

        relatedUserFour = Persistence.otpUsers.getById(relatedUserFour.id);
        assertFalse(relatedUserFour.dependents.contains(dependentUserFour.id));

        setAuthDisabled(false);
    }

    @Test
    void canGetDependentMobilityProfile() throws Exception {
        String path = String.format(
            "api/secure/user/getdependentmobilityprofile?%s=%s,%s",
            DEPENDENT_USER_IDS,
            dependentUserThree.id,
            dependentUserFour.id
        );

        HttpResponseValues responseValues = makeGetRequest(path, getMockHeaders(relatedUserFour));
        assertEquals(HttpStatus.FORBIDDEN_403, responseValues.status);

        var mobilityProfile = new MobilityProfile();
        mobilityProfile.mobilityDevices = Set.of("service animal", "electric wheelchair", "white cane");
        mobilityProfile.updateMobilityMode();
        dependentUserFour.mobilityProfile = mobilityProfile;
        dependentUserFour.name = "dependent-user-four-name";

        createTrustedCompanionship(relatedUserFour, dependentUserFour);

        responseValues = makeGetRequest(path, getMockHeaders(relatedUserFour));
        assertEquals(HttpStatus.OK_200, responseValues.status);
        List<MobilityProfileLite> mobilityProfileLites = JsonUtils.getPOJOFromJSONAsList(responseValues.responseBody, MobilityProfileLite.class);
        assert mobilityProfileLites != null;
        assertEquals(new MobilityProfileLite(dependentUserFour), mobilityProfileLites.get(0));
    }

    /**
     * Create trusted companion relationship.
     */
    private static void createTrustedCompanionship(OtpUser relatedUser, OtpUser dependentUser) {
        relatedUser.dependents.add(dependentUser.id);
        Persistence.otpUsers.replace(relatedUser.id, relatedUser);
        dependentUser.relatedUsers.add(new RelatedUser(
            relatedUser.email,
            RelatedUser.RelatedUserStatus.CONFIRMED,
            nickname
        ));
        Persistence.otpUsers.replace(dependentUser.id, dependentUser);
    }
}
