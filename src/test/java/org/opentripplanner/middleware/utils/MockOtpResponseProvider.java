package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.OtpResponse;

import java.time.DayOfWeek;
import java.util.Map;

/**
 * Provides a set of mock OTP responses in the order they are expected to be used.
 */
public class MockOtpResponseProvider {
    private int index = 0;
    private final Map<DayOfWeek, OtpResponse> mockResponses;

    public MockOtpResponseProvider(Map<DayOfWeek, OtpResponse> mockResponses) {
        this.mockResponses = mockResponses;
    }

    public OtpResponse getMockResponse(OtpRequest otpRequest) {
        index++;
        return mockResponses.get(otpRequest.dateTime.getDayOfWeek());
    }

    public boolean areAllMocksUsed() {
        return index == mockResponses.size();
    }
}
