package org.opentripplanner.middleware.otp.core.routing.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * <strong>Fare support is very, very preliminary.</strong>
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Money {

    private int cents;

    public Money() {}

    public void setCents(int cents) {
        this.cents = cents;
    }

    public int getCents() {
        return cents;
    }

    @Override
    public String toString() {
        return "Money{" +
                "cents=" + cents +
                '}';
    }
}
