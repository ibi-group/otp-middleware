package org.opentripplanner.middleware.i18n;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The purpose of Messages is to read supply Message.properties to underlying calling code... The
 * ENUM's enumerated values should be named to reflect the property names inside of
 * Message.properties
 */
public enum Message {
    TRIP_REMINDER_NOTIFICATION;

    private static final Logger LOG = LoggerFactory.getLogger(Message.class);

    public String get(Locale l) {
        if (l == null) {
            l = Locale.ROOT;
        }
        String name = name();
        String className = this.getClass().getSimpleName();
        try {
            ResourceBundle resourceBundle = ResourceBundle.getBundle(className, l);
            return resourceBundle.getString(name);
        } catch (MissingResourceException e) {
            LOG.warn("No entry in {}.properties file could be found for string {}", className, name);
            return name;
        }
    }

    public String get() {
        return get(Locale.getDefault());
    }
}