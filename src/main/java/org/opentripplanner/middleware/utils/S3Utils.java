package org.opentripplanner.middleware.utils;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
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
    private static S3Client getAmazonS3() {
        S3ClientBuilder amazonS3ClientBuilder = S3Client.builder();
        if (hasConfigProperty("AWS_PROFILE")) {
            amazonS3ClientBuilder.credentialsProvider(
                ProfileCredentialsProvider.create(getConfigPropertyAsText("AWS_PROFILE"))
            );
        }
        return amazonS3ClientBuilder.build();
    }

    /**
     * Get a list of items in a folder inside a bucket. An empty string or "/" as the folderName will return the
     * list of files at the root of a bucket.
     */
    public static List<CDPFile> getFolderListing(String bucketName, String folderName) {
        String prefix = folderName == null || folderName.isEmpty() || "/".equals(folderName)
            ? ""
            : folderName.replaceAll("/+$", "") + "/";
        List<CDPFile> cdpFiles = new ArrayList<>();
        ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
            .bucket(bucketName)
            .prefix(prefix)
            .build();
        try (S3Client s3Client = getAmazonS3()) {
            s3Client.listObjectsV2Paginator(listObjectsRequest).contents().forEach(objectSummary ->
                cdpFiles.add(new CDPFile(
                    objectSummary.key(),
                    prefix.isEmpty() ? objectSummary.key() : objectSummary.key().substring(prefix.length()),
                    objectSummary.size()
                ))
            );
        }

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
        S3Presigner.Builder presignerBuilder = S3Presigner.builder();
        if (hasConfigProperty("AWS_PROFILE")) {
            presignerBuilder.credentialsProvider(
                ProfileCredentialsProvider.create(getConfigPropertyAsText("AWS_PROFILE"))
            );
        }
        try (S3Presigner presigner = presignerBuilder.build()) {
            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMillis(expiration))
                    .getObjectRequest(request -> request.bucket(bucketName).key(fileKey))
                    .build()
            );
            return presignedRequest.url();
        }
    }

    /**
     * Upload an object to S3.
     */
    public static void putObject(String bucketName, String folderAndFileName, File file) throws S3Exception {
        try (S3Client s3Client = getAmazonS3()) {
            s3Client.putObject(
                PutObjectRequest.builder().bucket(bucketName).key(folderAndFileName).build(),
                RequestBody.fromFile(file)
            );
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
        try (S3Client s3Client = getAmazonS3()) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(folderAndFileName)
                .build());
            LOG.info("Removing {} from s3 bucket {}", folderAndFileName, bucketName);
        } catch (Exception e) {
            // If some unexpected exception is thrown by AWS, catch it, report to Bugsnag, and throw.
            String message = "Unable to delete object";
            S3Exception exception = new S3Exception(bucketName, folderAndFileName, message, e);
            BugsnagReporter.reportErrorToBugsnag(message, folderAndFileName, exception);
            throw exception;
        }
    }
}
