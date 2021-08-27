package org.opentripplanner.middleware.cdp;

/**
 * Used to define a trip data upload status. Trip uploads will remain 'pending' until successfully uploaded, at which
 * point the status is set to 'completed'.
 */
public enum TripHistoryUploadStatus {
    /**
     * Once a trip data upload for a day has been completed it's status is set to completed.
     */
    COMPLETED("COMPLETED"),
    /**
     * Trip data uploads which are waiting to be processed.
     */
    PENDING("PENDING");

    private final String value;

    TripHistoryUploadStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
