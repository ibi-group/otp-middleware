package org.opentripplanner.middleware.utils;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.ConfigUtils.hasConfigProperty;

/**
 * Manages all interactions with AWS S3.
 */
public class S3Utils {

    public static Logger LOG = LoggerFactory.getLogger(S3Utils.class);

    /**
     * Create connection to AWS S3.
     */
    private static AmazonS3 getAmazonS3() {
        AmazonS3ClientBuilder amazonS3ClientBuilder = AmazonS3ClientBuilder.standard();
        if (hasConfigProperty("AWS_PROFILE")) {
            amazonS3ClientBuilder.withCredentials(new ProfileCredentialsProvider(getConfigPropertyAsText("AWS_PROFILE")));
        }
        return amazonS3ClientBuilder.build();
    }

    /**
     * Upload an object to S3.
     */
    public static void putObject(String bucketName,
                                 String folderAndFileName,
                                 File file) throws S3Exception {
        try {
            AmazonS3 s3Client = getAmazonS3();
            s3Client.putObject(bucketName, folderAndFileName, file);
            LOG.info("Uploading to AWS: {}/{}", bucketName, folderAndFileName);
        } catch (Exception e) {
            // If some unexpected exception is thrown by AWS, catch it, report to Bugsnag, and throw.
            String message = "Unable to create object";
            S3Exception S3Exception = new S3Exception(bucketName, folderAndFileName, message, e);
            BugsnagReporter.reportErrorToBugsnag(message, folderAndFileName, S3Exception);
            throw S3Exception;
        }
    }

    /**
     * Delete an object on S3.
     */
    public static void deleteObject(String bucketName, String folderAndFileName) throws Exception {
        try {
            AmazonS3 s3Client = getAmazonS3();
            if (s3Client.doesObjectExist(bucketName, folderAndFileName)) {
                LOG.info("Removing {} from s3 bucket {}", folderAndFileName, bucketName);
                s3Client.deleteObject(bucketName, folderAndFileName);
            }
        } catch (Exception e) {
            // If some unexpected exception is thrown by AWS, catch it, report to Bugsnag, and throw.
            String message = "Unable to delete object";
            S3Exception S3Exception = new S3Exception(bucketName, folderAndFileName, message, e);
            BugsnagReporter.reportErrorToBugsnag(message, folderAndFileName, S3Exception);
            throw S3Exception;
        }
    }
}


