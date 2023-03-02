package org.opentripplanner.middleware.otp.response;

import java.util.Date;
import java.util.Objects;

public class LocalizedAlert {
    public String alertHeaderText;
    public String alertDescriptionText;
    public String alertUrl;
    public Date effectiveStartDate;

    public Date effectiveEndDate;

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
