package org.opentripplanner.middleware.models;

/** A related user is a companion or observer requested by a dependent. */
public class RelatedUser {
    public enum RelatedUserStatus {
        PENDING, CONFIRMED, INVALID
    }

    public String userId;
    public String email;
    public RelatedUserStatus status;
    public boolean acceptDependentEmailSent;

    public RelatedUser() {
        // Required for JSON deserialization.
    }

    public RelatedUser(String userId, String email, RelatedUserStatus status) {
        this.userId = userId;
        this.email = email;
        this.status = status;
    }
}

