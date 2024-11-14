package org.opentripplanner.middleware.connecteddataplatform;

/**
 * Used to define an upload status. Uploads will remain 'pending' until successfully uploaded, at which
 * point the status is set to 'completed'.
 */
public enum IntervalUploadStatus {
    /**
     * Once a trip data upload for a day has been completed it's status is set to completed.
     */
    COMPLETED("COMPLETED"),
    /**
     * Trip data uploads which are waiting to be processed.
     */
    PENDING("PENDING");

    private final String value;

    IntervalUploadStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
