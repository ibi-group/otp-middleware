package org.opentripplanner.middleware.models;

/** A guardian user is a companion or observer requested by a dependent. */
public class GuardianUser {
    public enum GuardianUserStatus {
        PENDING, CONFIRMED, INVALID
    }

    public String userId;
    public String email;
    public GuardianUserStatus status;

    public GuardianUser() {
        // Required for JSON deserialization.
    }

    public GuardianUser(String userId, String email, GuardianUserStatus status) {
        this.userId = userId;
        this.email = email;
        this.status = status;
    }
}

