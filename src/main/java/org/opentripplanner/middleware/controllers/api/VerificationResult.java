package org.opentripplanner.middleware.controllers.api;

import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;

import java.io.Serializable;

/**
 * Simplified verification result to wrap Twilio SMS verification responses.
 */
public class VerificationResult implements Serializable {
    public String sid;
    public String status;
    private boolean valid;

    /**
     * Wrapper for {@link VerificationCheck}, which is the result of checking the validity of an SMS verification code
     * (e.g., 123456).
     */
    public VerificationResult(VerificationCheck check) {
        sid = check.getSid();
        status = check.getStatus();
        valid = check.getValid();
    }

    /**
     * Wrapper for {@link Verification}, which is the result of sending an SMS verification code to a phone number.
     */
    public VerificationResult(Verification verification) {
        sid = verification.getSid();
        status = verification.getStatus();
        valid = verification.getValid();
    }
}
