package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.middleware.models.ApiKeyDetails;
import org.opentripplanner.middleware.models.ApiUser;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.ApiGatewayClientBuilder;
import software.amazon.awssdk.services.apigateway.model.GetApiKeyRequest;
import software.amazon.awssdk.services.apigateway.model.GetUsagePlanKeyRequest;
import software.amazon.awssdk.services.apigateway.model.GetUsageResponse;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.ConfigUtils.hasConfigProperty;

/**
 * End-to-end tests for API Gateway operations. Requires RUN_E2E=true, AWS credentials,
 * and DEFAULT_USAGE_PLAN_ID set to a usage plan in the configured AWS account.
 */
class ApiGatewayUtilsTest extends OtpMiddlewareTestEnvironment {
    private static final String TEST_EMAIL_PREFIX = "otp-middleware-apigateway-test-" + UUID.randomUUID();
    private static final Set<String> CREATED_KEY_IDS = ConcurrentHashMap.newKeySet();
    private static String usagePlanId;

    @BeforeAll
    static void setUp() {
        assumeTrue(IS_END_TO_END, "API Gateway tests require RUN_E2E=true");
        usagePlanId = getConfigPropertyAsText("DEFAULT_USAGE_PLAN_ID");
        assertNotNull(usagePlanId, "DEFAULT_USAGE_PLAN_ID must be configured for end-to-end tests");
        assertFalse(usagePlanId.isBlank(), "DEFAULT_USAGE_PLAN_ID must not be blank");
    }

    @AfterAll
    static void removeTestApiKeys() {
        List<String> cleanupFailures = new ArrayList<>();
        for (String keyId : CREATED_KEY_IDS) {
            if (!ApiGatewayUtils.deleteApiKey(new ApiKeyDetails(keyId))) {
                cleanupFailures.add(keyId);
            }
        }
        assertTrue(cleanupFailures.isEmpty(), "Unable to remove all test API keys: " + cleanupFailures);
    }

    @Test
    void createsApiKeyAndAssignsItToConfiguredUsagePlan() throws CreateApiKeyException {
        ApiKeyDetails key = createTrackedApiKey();

        assertNotNull(key.keyId);
        assertNotNull(key.name);
        assertNotNull(key.value);
        try (ApiGatewayClient gateway = newApiGatewayClient()) {
            assertEquals(key.keyId, gateway.getUsagePlanKey(GetUsagePlanKeyRequest.builder()
                .usagePlanId(usagePlanId)
                .keyId(key.keyId)
                .build()).id());
        }
    }

    @Test
    void deletesApiKey() throws CreateApiKeyException {
        ApiKeyDetails key = createTrackedApiKey();

        assertTrue(ApiGatewayUtils.deleteApiKey(key));
        try (ApiGatewayClient gateway = newApiGatewayClient()) {
            assertThrows(
                software.amazon.awssdk.services.apigateway.model.NotFoundException.class,
                () -> gateway.getApiKey(GetApiKeyRequest.builder().apiKey(key.keyId).includeValue(false).build())
            );
        }
    }

    @Test
    void retrievesUsageLogsForApiKey() throws CreateApiKeyException {
        ApiKeyDetails key = createTrackedApiKey();

        List<GetUsageResponse> usage = ApiGatewayUtils.getUsageLogsForKey(
            key.keyId,
            LocalDate.now(ZoneOffset.UTC).minusDays(1).format(DateTimeUtils.DEFAULT_DATE_FORMATTER),
            LocalDate.now(ZoneOffset.UTC).format(DateTimeUtils.DEFAULT_DATE_FORMATTER)
        );

        assertFalse(usage.isEmpty());
        assertTrue(usage.stream().anyMatch(result -> usagePlanId.equals(result.usagePlanId())));
    }

    @Test
    void retrievesUsageLogsForMultipleApiKeys() throws CreateApiKeyException {
        ApiKeyDetails firstKey = createTrackedApiKey();
        ApiKeyDetails secondKey = createTrackedApiKey();

        List<GetUsageResponse> usage = ApiGatewayUtils.getUsageLogsForKeys(
            List.of(firstKey, secondKey),
            LocalDate.now(ZoneOffset.UTC).minusDays(1).format(DateTimeUtils.DEFAULT_DATE_FORMATTER),
            LocalDate.now(ZoneOffset.UTC).format(DateTimeUtils.DEFAULT_DATE_FORMATTER)
        );

        assertFalse(usage.isEmpty());
        assertTrue(usage.stream().anyMatch(result -> usagePlanId.equals(result.usagePlanId())));
    }

    private static ApiKeyDetails createTrackedApiKey() throws CreateApiKeyException {
        ApiUser user = new ApiUser();
        user.id = TEST_EMAIL_PREFIX;
        user.email = TEST_EMAIL_PREFIX + "@example.com";

        ApiKeyDetails key = ApiGatewayUtils.createApiKey(user, usagePlanId);
        if (key.keyId != null) {
            CREATED_KEY_IDS.add(key.keyId);
        }
        return key;
    }

    private static ApiGatewayClient newApiGatewayClient() {
        ApiGatewayClientBuilder builder = ApiGatewayClient.builder();
        if (hasConfigProperty("AWS_PROFILE")) {
            builder.credentialsProvider(ProfileCredentialsProvider.create(getConfigPropertyAsText("AWS_PROFILE")));
        }
        return builder.build();
    }
}
