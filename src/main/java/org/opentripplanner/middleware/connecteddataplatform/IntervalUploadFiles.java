package org.opentripplanner.middleware.connecteddataplatform;

import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
import org.opentripplanner.middleware.utils.FileUtils;
import org.opentripplanner.middleware.utils.S3Exception;
import org.opentripplanner.middleware.utils.S3Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME;
import static org.opentripplanner.middleware.connecteddataplatform.ConnectedDataManager.ZIP_FILE_EXTENSION;

/**
 * Helper class for upload job file handling.
 */
public class IntervalUploadFiles implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(IntervalUploadFiles.class);

    private final boolean isTest;

    private final String zipFileName;

    private final String tempZipFile;

    private final String tempDataFile;

    public IntervalUploadFiles(String filePrefix, String coreExtension, boolean isTest) {
        this.isTest = isTest;

        zipFileName = String.join(".", filePrefix, ZIP_FILE_EXTENSION);
        String tempFileFolder = FileUtils.getTempDirectory().getAbsolutePath();
        tempZipFile = String.join(File.separator, tempFileFolder, zipFileName);
        tempDataFile = String.join(File.separator, tempFileFolder, String.join(".", filePrefix, coreExtension));
    }

    public String getTempDataFile() {
        return tempDataFile;
    }

    /**
     * Compress and upload the data file.
     */
    public void compressAndUpload(String folder) throws IOException {
        FileUtils.addSingleFileToZip(tempDataFile, tempZipFile);
        try {
            S3Utils.putObject(
                CONNECTED_DATA_PLATFORM_S3_BUCKET_NAME,
                String.format(
                    "%s/%s",
                    folder,
                    zipFileName
                ),
                new File(tempZipFile)
            );
        } catch (S3Exception e) {
            String message = String.format("Error uploading (%s) to S3", zipFileName);
            LOG.error(message);
            BugsnagReporter.reportErrorToBugsnag(message, e);
        }
    }

    @Override
    public void close() throws IOException {
        // Delete the temporary files here, to cover S3 upload success or failure.
        try {
            LOG.info("Deleting zip file {}.", tempZipFile);
            FileUtils.deleteFile(tempDataFile);
            if (!isTest) {
                FileUtils.deleteFile(tempZipFile);
            } else {
                LOG.warn("In test mode, temp zip file {} not deleted. This is expected to be deleted by the calling test.",
                    tempZipFile
                );
            }
        } catch (IOException e) {
            LOG.error("Failed to delete temp files", e);
            throw e;
        }
    }
}
