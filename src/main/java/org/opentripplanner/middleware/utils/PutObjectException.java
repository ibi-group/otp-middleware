package org.opentripplanner.middleware.utils;

/**
 * This is the exception for issues when putting an object onto an S3 bucket.
 */
public class PutObjectException extends Exception {
    public PutObjectException(String bucketName, String folderAndFileName, Exception cause) {
        super(
            String.format(
                "Unable to create object (%s) on S3 bucket (%s) (cause: %s)",
                folderAndFileName,
                bucketName,
                cause.toString()
            ),
            cause
        );
    }
}
