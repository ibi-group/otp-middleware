package org.opentripplanner.middleware.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a third-party application developer, which has a set of AWS API Gateway API keys which they can use to
 * access otp-middleware's endpoints (as well as the geocoding and OTP endpoints).
 */
public class ApiUser extends AbstractUser {
    public List<String> apiKeyIds = new ArrayList<>();
}
