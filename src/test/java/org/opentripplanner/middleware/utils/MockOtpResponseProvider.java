package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.otp.OtpRequest;
import org.opentripplanner.middleware.otp.response.OtpResponse;

import java.util.List;

/**
 * Provides a set of mock OTP responses in the order they are expected to be used.
 */
public class MockOtpResponseProvider {
    private int index = 0;
    private final List<OtpResponse> mockResponses;

    public MockOtpResponseProvider(List<OtpResponse> mockResponses) {
        this.mockResponses = mockResponses;
    }

    public OtpResponse getMockResponse(OtpRequest otpRequest) {
        // otpRequest is ignored, and the next response is given.
        // If index is out of bounds, an error will be thrown.
        return mockResponses.get(index++);
    }

    public boolean areAllMocksUsed() {
        return index == mockResponses.size();
    }
}
