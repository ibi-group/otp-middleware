package org.opentripplanner.middleware.otp.response;

public class LocalizedAlert {
    public String alertHeaderText;
    public String alertDescriptionText;
    public String alertUrl;
    public int effectiveStartDate;

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
