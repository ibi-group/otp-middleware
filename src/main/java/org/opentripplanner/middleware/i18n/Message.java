package org.opentripplanner.middleware.i18n;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Supplies content from Message.properties to underlying calling code...
 * The ENUM's enumerated values should be named to reflect the property names inside of
 * Message.properties.
 */
public enum Message {
    LABEL_AND_CONTENT,
    SMS_STOP_NOTIFICATIONS,
    TRIP_EMAIL_SUBJECT,
    TRIP_EMAIL_SUBJECT_FOR_USER,
    TRIP_EMAIL_GREETING,
    TRIP_EMAIL_FOOTER,
    TRIP_EMAIL_MANAGE_NOTIFICATIONS,
    TRIP_LINK_TEXT,
    TRIP_ALERT_FOUND_SINGULAR,
    TRIP_ALERT_FOUND_PLURAL,
    TRIP_ALERT_NEW_SINGULAR,
    TRIP_ALERT_NEW_PLURAL,
    TRIP_ALERT_RESOLVED_SINGULAR,
    TRIP_ALERT_RESOLVED_PLURAL,
    TRIP_ALERT_NEW_AND_RESOLVED,
    TRIP_ALERT_NOTIFICATION,
    TRIP_ALERT_ALL_RESOLVED,
    TRIP_ALERT_ALL_RESOLVED_WITH_LIST,
    TRIP_DELAY_NOTIFICATION,
    TRIP_DELAY_ARRIVE,
    TRIP_DELAY_DEPART,
    TRIP_DELAY_ON_TIME,
    TRIP_DELAY_EARLY,
    TRIP_DELAY_LATE,
    TRIP_DELAY_MINUTES,
    TRIP_NOT_FOUND_NOTIFICATION,
    TRIP_NO_LONGER_POSSIBLE_NOTIFICATION,
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
