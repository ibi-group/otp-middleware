package org.opentripplanner.middleware.models;

import java.util.ArrayList;
import java.util.List;

/**
 * @schema ApiUser
 *   allOf:
 *    - $ref: '#/components/schemas/AbstractUser'
 *    - type: object
 *      properties:
 *        apiKeyIds:
 *          type: array
 *          items:
 *              type: string
 *          description: Holds the API keys assigned to the user.
 *        appName:
 *          type: string
 *          description: The name of the application built by this user.
 *        appPurpose:
 *          type: string
 *          description: The purpose of the application built by this user.
 *        appUrl:
 *          type: string
 *          description: The URL of the application built by this user.
 *        company:
 *          type: string
 *          description: The company or organization that this user belongs to.
 *        hasConsentedToTerms:
 *          type: boolean
 *          description: Whether the user has consented to the terms of use.
 *        name:
 *          type: string
 *          description: The name of this user.
 */

/**
 * Represents a third-party application developer, which has a set of AWS API Gateway API keys which they can use to
 * access otp-middleware's endpoints (as well as the geocoding and OTP endpoints).
 */
public class ApiUser extends AbstractUser {
    /** Holds the API keys assigned to the user. */
    public List<String> apiKeyIds = new ArrayList<>();

    /** The name of the application built by this user. */
    public String appName;

    /** The purpose of the application built by this user. */
    public String appPurpose;

    /** The URL of the application built by this user. */
    public String appUrl;

    /** The company or organization that this user belongs to. */
    public String company;

    // FIXME: Move this member to AbstractUser?
    /** Whether the user has consented to terms of use. */
    public boolean hasConsentedToTerms;

    // FIXME: Move this member to AbstractUser?
    /** The name of this user */
    public String name;
}
