package org.opentripplanner.middleware.otp.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;
import java.util.Objects;

public class LocalizedAlert {
    public String alertHeaderText;
    public String alertDescriptionText;
    public String alertUrl;
    public Date effectiveStartDate;

    public Date effectiveEndDate;

    public String id;

    // Getters for the notification template processor.

    public String getAlertHeaderText() {
        return alertHeaderText;
    }

    public String getAlertDescriptionText() {
        return alertDescriptionText;
    }

    /**
     * Line returns are not preserved if using the HTML email renderer,
     * so we insert line returns as line-break (br) tags to match the itinerary-body UI.
     */
    @JsonIgnore
    public String getAlertDescriptionForHtml() {
        return alertDescriptionText != null
            ? alertDescriptionText.replace("\n", "<br/>\n")
            : "";
    }

    @Override
    public int hashCode() {
        return Objects.hash(alertHeaderText, alertDescriptionText, alertUrl, effectiveStartDate, effectiveEndDate);
    }

    public boolean equals(Object o) {
        if (!(o instanceof LocalizedAlert)) {
            return false;
        }
        LocalizedAlert ao = (LocalizedAlert) o;
        if (alertDescriptionText == null) {
            if (ao.alertDescriptionText != null) {
                return false;
            }
        } else {
            if (!alertDescriptionText.equals(ao.alertDescriptionText)) {
                return false;
            }
        }
        if (alertHeaderText == null) {
            if (ao.alertHeaderText != null) {
                return false;
            }
        } else {
            if (!alertHeaderText.equals(ao.alertHeaderText)) {
                return false;
            }
        }
        if (alertUrl == null) {
            return ao.alertUrl == null;
        } else {
            return alertUrl.equals(ao.alertUrl);
        }
    }
}
