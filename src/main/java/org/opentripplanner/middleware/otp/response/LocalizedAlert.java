package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.Date;
import java.util.Objects;
import java.util.regex.Pattern;

public class LocalizedAlert {
    public String alertHeaderText;
    public String alertDescriptionText;
    public String alertUrl;
    public Date effectiveStartDate;

    public Date effectiveEndDate;

    public String id;

    /** Regex to find both Windows and Unix line endings. */
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("\\R");

    /** Main, passive constructor for persistence */
    public LocalizedAlert() {
        // Does nothing
    }

    /** Constructor, mainly for tests and object comparisons. */
    public LocalizedAlert(String header, String description) {
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
        return Objects.hash(getAlertHeaderText(), getAlertDescriptionText(), alertUrl, effectiveStartDate);
    }

    public boolean equals(Object o) {
        if (!(o instanceof LocalizedAlert)) {
            return false;
        }
        LocalizedAlert ao = (LocalizedAlert) o;
        if (!getAlertDescriptionText().equals(ao.getAlertDescriptionText())) {
            return false;
        }
        if (!getAlertHeaderText().equals(ao.getAlertHeaderText())) {
            return false;
        }
        if (alertUrl == null) {
            return ao.alertUrl == null;
        } else {
            return alertUrl.equals(ao.alertUrl);
        }
    }
}
