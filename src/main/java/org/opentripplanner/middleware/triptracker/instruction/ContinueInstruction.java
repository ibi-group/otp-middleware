package org.opentripplanner.middleware.triptracker.instruction;

import org.opentripplanner.middleware.otp.response.Step;

import java.util.Locale;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

public class ContinueInstruction extends SelfLegInstruction {
    public ContinueInstruction(Step legStep, Locale locale) {
        this.legStep = legStep;
        this.locale = locale;
    }

    @Override
    public String build() {
        if (isNotEmpty(legStep) && isNotEmpty(legStep.streetName)) {
            // TODO: i18n
            return String.format("Continue on %s", legStep.streetName);
        }
        return NO_INSTRUCTION;
    }
}
