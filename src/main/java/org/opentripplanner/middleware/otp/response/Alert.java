package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.Date;
import java.util.Objects;
import java.util.regex.Pattern;

public class Alert {

    /**
     * Header of the alert, if available.
     */
    public String alertHeaderText;

    /**
     * Long description of the alert.
     */
    public String alertDescriptionText;

    /**
     * Url with more information.
     */
    public String alertUrl;

    /**
     * Time when this alert comes into effect. Format: Unix timestamp in seconds.
     */
    public Date effectiveStartDate;

    /**
     * Time when this alert is not in effect anymore. Format: Unix timestamp in seconds.
     */
    public Date effectiveEndDate;

    /**
     * Global object ID provided by Relay. This value can be used to re-fetch this object using node query.
     */
    public String id;

    /** Regex to find both Windows and Unix line endings. */
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("\\R");

    /** Main, passive constructor for persistence */
    public Alert() {
        // Does nothing
    }

    /** Constructor, mainly for tests and object comparisons. */
    public Alert(String header, String description) {
        alertHeaderText = header;
        alertDescriptionText = description;
    }

    /** Header getter for the notification template processor. */
    public String getAlertHeaderText() {
        return alertHeaderText != null ? alertHeaderText : "";
    }

    /** Description getter for the notification template processor. */
    public String getAlertDescriptionText() {
        return alertDescriptionText != null ? alertDescriptionText : "";
    }

    /**
     * Line returns are not preserved if using the HTML email renderer,
     * so we insert line returns as line-break (br) tags to match the itinerary-body UI.
     */
    @JsonIgnore
    @BsonIgnore
    public String getAlertDescriptionForHtml() {
        return alertDescriptionText != null
            ? NEWLINE_PATTERN.matcher(alertDescriptionText).replaceAll("<br/>" + System.lineSeparator())
            : "";
    }

    @Override
    public int hashCode() {
        // Exclude effectiveEndDate from the hash code for cases where a given alert is "extended",
        // e.g. incidents that take longer to resolve than initially planned.
        // Use getters instead of fields to treat null same as "" for comparison purposes.
        return Objects.hash(getAlertHeaderText(), getAlertDescriptionText(), alertUrl, effectiveStartDate);
    }

    public boolean equals(Object o) {
        if (!(o instanceof Alert)) {
            return false;
        }
        Alert ao = (Alert) o;
        if (
            !getAlertDescriptionText().equals(ao.getAlertDescriptionText()) ||
            !getAlertHeaderText().equals(ao.getAlertHeaderText())
        ) {
            return false;
        }
        if (alertUrl == null) {
            return ao.alertUrl == null;
        } else {
            return alertUrl.equals(ao.alertUrl);
        }
    }
}
