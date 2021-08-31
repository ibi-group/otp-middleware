package org.opentripplanner.middleware.utils;

/**
 * This exception is for issues related to working with S3 buckets.
 */
public class S3Exception extends Exception {
    public S3Exception(String bucketName, String folderAndFileName, String message, Exception cause) {
        super(
            String.format(
                "%s (%s) on S3 bucket (%s) (cause: %s)",
                message,
                folderAndFileName,
                bucketName,
                cause.toString()
            ),
            cause
        );
    }
}
