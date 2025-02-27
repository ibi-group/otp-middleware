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
        OtpResponse otpResponse = null;
        switch (otpRequest.dateTime.getDayOfWeek()) {
            case THURSDAY:
                otpResponse = mockResponses.get(0);
                break;
            case FRIDAY:
                otpResponse = mockResponses.get(1);
                break;
            case SATURDAY:
                otpResponse = mockResponses.get(2);
                break;
            case SUNDAY:
                otpResponse = mockResponses.get(3);
                break;
            case MONDAY:
                otpResponse = mockResponses.get(4);
                break;
            case TUESDAY:
                otpResponse = mockResponses.get(5);
                break;
            case WEDNESDAY:
                otpResponse = mockResponses.get(6);
                break;
        }
        index++;
        return otpResponse;
    }

    public boolean areAllMocksUsed() {
        return index == mockResponses.size();
    }
}
