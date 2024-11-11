package org.opentripplanner.middleware.models;

import java.util.Objects;

public class MobilityProfileLite {
    public String userId;
    public String mobilityMode;
    public String email;
    public String name;

    public MobilityProfileLite() {
    }

    public MobilityProfileLite(OtpUser user) {
        this.userId = user.id;
        this.mobilityMode = (user.mobilityProfile != null) ? user.mobilityProfile.mobilityMode : null;
        this.email = user.email;
        this.name = user.name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MobilityProfileLite that = (MobilityProfileLite) o;
        return
            Objects.equals(userId, that.userId) &&
            Objects.equals(mobilityMode, that.mobilityMode) &&
            Objects.equals(email, that.email) &&
            Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, mobilityMode, email, name);
    }
}
