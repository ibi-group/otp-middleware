package org.opentripplanner.middleware.triptracker.instruction;

import org.apache.logging.log4j.util.Strings;
import org.opentripplanner.middleware.otp.response.Step;

import java.util.Locale;

public class ContinueInstruction extends SelfLegInstruction {
    public ContinueInstruction(Step legStep, Locale locale) {
        this.legStep = legStep;
        this.locale = locale;
    }

    @Override
    public String build() {
        if (legStep != null && !Strings.isBlank(legStep.streetName)) {
            // TODO: i18n
            return String.format("Continue on %s", legStep.streetName);
        }
        return NO_INSTRUCTION;
    }
}
