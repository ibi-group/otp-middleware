package org.opentripplanner.middleware.tripmonitor;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.OtpUser;
import org.opentripplanner.middleware.models.RelatedUser;
import org.opentripplanner.middleware.persistence.Persistence;
import org.opentripplanner.middleware.testutils.ApiTestUtils;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import org.opentripplanner.middleware.testutils.PersistenceTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.middleware.auth.Auth0Connection.setAuthDisabled;
import static org.opentripplanner.middleware.testutils.PersistenceTestUtils.deleteOtpUser;

public class TrustedCompanionTest extends OtpMiddlewareTestEnvironment {
    private static OtpUser relatedUserOne;
    private static OtpUser dependentUserOne;

    @BeforeAll
    public static void setUp() {
        setAuthDisabled(false);
        relatedUserOne = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("related-user-one"));
        dependentUserOne = PersistenceTestUtils.createUser(ApiTestUtils.generateEmailAddress("dependent-one"));
    }

    @AfterAll
    public static void tearDown() {
        deleteOtpUser(IS_END_TO_END,
            relatedUserOne,
            dependentUserOne
        );
    }

    @Test
    void canManageAcceptDependentEmail() {
        dependentUserOne.relatedUsers.add(new RelatedUser(relatedUserOne.id, relatedUserOne.email, RelatedUser.RelatedUserStatus.PENDING));
        Persistence.otpUsers.replace(dependentUserOne.id, dependentUserOne);
        TrustedCompanion.manageAcceptDependentEmail(dependentUserOne, true);
        assertTrue(dependentUserOne.relatedUsers.get(0).acceptDependentEmailSent);
    }
}