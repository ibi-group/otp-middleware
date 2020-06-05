package org.opentripplanner.middleware.otp;

/**
 * Defines the available interfaces to OTP
 */
public interface OtpDispatcher {
    OtpDispatcherResponse getPlan(String query, String endPoint);
}
