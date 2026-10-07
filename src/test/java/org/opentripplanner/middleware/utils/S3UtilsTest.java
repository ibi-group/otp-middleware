package org.opentripplanner.middleware.utils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentripplanner.middleware.testutils.OtpMiddlewareTestEnvironment;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opentripplanner.middleware.utils.ConfigUtils.getConfigPropertyAsText;

/**
 * End-to-end tests for S3 operations. Requires RUN_E2E=true, AWS credentials,
 * and CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME set to a writable test bucket.
 */
class S3UtilsTest extends OtpMiddlewareTestEnvironment {
    private static final String TEST_PREFIX = "otp-middleware-s3-test/" + UUID.randomUUID();
    private static final Set<String> CREATED_OBJECT_KEYS = ConcurrentHashMap.newKeySet();
    private static String bucketName;

    @BeforeAll
    static void setUp() {
        assumeTrue(IS_END_TO_END, "S3 tests require RUN_E2E=true");
        bucketName = getConfigPropertyAsText("CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME");
        assertNotNull(bucketName, "CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME must be configured");
        assertFalse(bucketName.isBlank(), "CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME must not be blank");
        assertFalse(bucketName.startsWith("<"), "Configure a real S3 bucket for end-to-end tests");
    }

    @AfterAll
    static void removeTestObjects() {
        List<String> cleanupFailures = new ArrayList<>();
        for (String key : CREATED_OBJECT_KEYS) {
            try {
                S3Utils.deleteObject(bucketName, key);
            } catch (S3Exception e) {
                cleanupFailures.add(key + ": " + e.getMessage());
            }
        }
        assertTrue(cleanupFailures.isEmpty(), "Unable to remove all test S3 objects: " + cleanupFailures);
    }

    @Test
    void listsObjectsInFolder(@TempDir Path tempDir) throws Exception {
        String folder = TEST_PREFIX + "/listing";
        String firstKey = folder + "/first.txt";
        String secondKey = folder + "/nested/second.txt";
        putTestObject(firstKey, createFile(tempDir, "first.txt", "first"));
        putTestObject(secondKey, createFile(tempDir, "second.txt", "second"));

        List<CDPFile> files = S3Utils.getFolderListing(bucketName, folder);

        assertEquals(2, files.size());
        assertTrue(files.stream().anyMatch(file ->
            file.key.equals(firstKey) && file.name.equals("first.txt") && file.size == 5
        ));
        assertTrue(files.stream().anyMatch(file ->
            file.key.equals(secondKey) && file.name.equals("nested/second.txt") && file.size == 6
        ));
    }

    @Test
    void createsDefaultExpirationDownloadLink(@TempDir Path tempDir) throws Exception {
        String key = TEST_PREFIX + "/default-expiration.txt";
        putTestObject(key, createFile(tempDir, "download.txt", "download"));

        URL url = S3Utils.getTemporaryDownloadLinkForObject(bucketName, key);

        assertNotNull(url);
        assertTrue(url.getProtocol().equals("https") || url.getProtocol().equals("http"));
        assertTrue(url.getQuery().contains("X-Amz-Signature="));
    }

    @Test
    void createsDownloadLinkWithSpecifiedExpiration(@TempDir Path tempDir) throws Exception {
        String key = TEST_PREFIX + "/custom-expiration.txt";
        putTestObject(key, createFile(tempDir, "custom-download.txt", "download"));

        URL url = S3Utils.getTemporaryDownloadLinkForObject(bucketName, key, 60_000);

        assertNotNull(url);
        assertTrue(url.getQuery().contains("X-Amz-Signature="));
    }

    @Test
    void uploadsObject(@TempDir Path tempDir) throws Exception {
        String folder = TEST_PREFIX + "/upload";
        String key = folder + "/uploaded.txt";
        putTestObject(key, createFile(tempDir, "upload.txt", "uploaded content"));

        List<CDPFile> files = S3Utils.getFolderListing(bucketName, folder);

        assertEquals(1, files.size());
        assertEquals(key, files.getFirst().key);
        assertEquals("uploaded.txt", files.getFirst().name);
        assertEquals(16, files.getFirst().size);
    }

    @Test
    void deletesObject(@TempDir Path tempDir) throws Exception {
        String folder = TEST_PREFIX + "/delete";
        String key = folder + "/to-delete.txt";
        putTestObject(key, createFile(tempDir, "to-delete.txt", "delete me"));

        S3Utils.deleteObject(bucketName, key);

        assertTrue(S3Utils.getFolderListing(bucketName, folder).isEmpty());
    }

    private static Path createFile(Path tempDir, String fileName, String contents) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, contents);
    }

    private static void putTestObject(String key, Path file) throws S3Exception {
        CREATED_OBJECT_KEYS.add(key);
        S3Utils.putObject(bucketName, key, file.toFile());
    }
}
