package org.opentripplanner.middleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.bson.codecs.pojo.annotations.BsonIgnore;

/** A related user is a companion or observer requested by a dependent. */
public class RelatedUser {
    public enum RelatedUserStatus {
        PENDING, CONFIRMED, INVALID
    }

    public String email;
    public RelatedUserStatus status = RelatedUserStatus.PENDING;
    public String acceptKey;
    public String nickname;

    public RelatedUser() {
        // Required for JSON deserialization.
    }

    public RelatedUser(String email, RelatedUserStatus status) {
        this(email, status, null);
    }

    public RelatedUser(String email, RelatedUserStatus status, String nickname) {
        this.email = email;
        this.status = status;
        this.nickname = nickname;
    }

    public RelatedUser(String email, RelatedUserStatus status, String nickname, String acceptKey) {
        this (email, status, nickname);
        this.acceptKey = acceptKey;
    }

    @JsonIgnore
    @BsonIgnore
    public boolean isConfirmed() {
        return status == RelatedUserStatus.CONFIRMED;
    }
}

