package org.opentripplanner.middleware.models;

/** A related user is a companion or observer requested by a dependent. */
public class RelatedUser {
    public enum RelatedUserStatus {
        PENDING, CONFIRMED, INVALID
    }

    public String email;
    public RelatedUserStatus status = RelatedUserStatus.PENDING;
    public String acceptKey;
    public String nickName;

    public RelatedUser() {
        // Required for JSON deserialization.
    }

    public RelatedUser(String email, RelatedUserStatus status, String nickName) {
        this.email = email;
        this.status = status;
        this.nickName = nickName;
    }

    public RelatedUser(String email, RelatedUserStatus status, String nickName, String acceptKey) {
        this (email, status, nickName);
        this.acceptKey = acceptKey;
    }
}

