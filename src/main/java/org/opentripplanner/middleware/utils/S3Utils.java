package org.opentripplanner.middleware.utils;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;
import static org.opentripplanner.middleware.utils.ConfigUtils.hasConfigProperty;

/**
 * Manages all interactions with AWS S3.
 */
public class S3Utils {

    public static final Logger LOG = LoggerFactory.getLogger(S3Utils.class);

    private S3Utils() {}

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
     * Get a list of items in a folder inside a bucket. An empty string or "/" as the folderName will return the
     * list of files at the root of a bucket.
     */
    public static List<CDPFile> getFolderListing(String bucketName, String folderName) {
        AmazonS3 s3Client = getAmazonS3();
        List<CDPFile> cdpFiles = new ArrayList<>();
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName);
        ObjectListing objectListing;

        do {
            objectListing = s3Client.listObjects(listObjectsRequest);
            for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
                // TODO: a less brittle way of getting the name, will probably be related to folder-based filtering
                cdpFiles.add(new CDPFile(objectSummary.getKey(), objectSummary.getKey().substring(folderName.length() + 1), objectSummary.getSize()));
            }
            listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());

        return cdpFiles;
    }

    /**
     * This method will generate a download link for a specific file in a specific bucket.
     * The download link is set to expire after 5 minutes by default.
     */
    public static URL getTemporaryDownloadLinkForObject(String bucketName, String fileKey) {
        // Default of 5 minutes
        return getTemporaryDownloadLinkForObject(bucketName, fileKey, 300000);
    }

    public static URL getTemporaryDownloadLinkForObject(String bucketName, String fileKey, int expiration) {
        AmazonS3 s3Client = getAmazonS3();
        Date formalExpiration = new java.util.Date();
        formalExpiration.setTime(formalExpiration.getTime() + expiration);

        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(bucketName, fileKey)
                        .withMethod(HttpMethod.GET)
                        .withExpiration(formalExpiration);
        return s3Client.generatePresignedUrl(generatePresignedUrlRequest);
    }

    /**
     * Upload an object to S3.
     */
    public static void putObject(String bucketName, String folderAndFileName, File file) throws S3Exception {
        try {
            AmazonS3 s3Client = getAmazonS3();
            s3Client.putObject(bucketName, folderAndFileName, file);
            LOG.info("Uploading to AWS: {}/{}", bucketName, folderAndFileName);
        } catch (Exception e) {
            // If some unexpected exception is thrown by AWS, catch it, report to Bugsnag, and throw.
            String message = "Unable to create object";
            S3Exception exception = new S3Exception(bucketName, folderAndFileName, message, e);
            BugsnagReporter.reportErrorToBugsnag(message, folderAndFileName, exception);
            throw exception;
        }
    }

    /**
     * Delete an object on S3.
     */
    public static void deleteObject(String bucketName, String folderAndFileName) throws S3Exception {
        try {
            AmazonS3 s3Client = getAmazonS3();
            if (s3Client.doesObjectExist(bucketName, folderAndFileName)) {
                LOG.info("Removing {} from s3 bucket {}", folderAndFileName, bucketName);
                s3Client.deleteObject(bucketName, folderAndFileName);
            }
        } catch (Exception e) {
            // If some unexpected exception is thrown by AWS, catch it, report to Bugsnag, and throw.
            String message = "Unable to delete object";
            S3Exception exception = new S3Exception(bucketName, folderAndFileName, message, e);
            BugsnagReporter.reportErrorToBugsnag(message, folderAndFileName, exception);
            throw exception;
        }
    }
}


