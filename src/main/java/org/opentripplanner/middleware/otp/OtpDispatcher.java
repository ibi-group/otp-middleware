package org.opentripplanner.middleware.otp;

public interface OtpDispatcher {
    OtpDispatcherResponse getPlan(String query);
}
