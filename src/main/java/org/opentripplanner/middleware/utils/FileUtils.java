package org.opentripplanner.middleware.utils;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * File utility class for extracting and parsing file content
 */
public class FileUtils {
    private static final Logger LOG = LoggerFactory.getLogger(FileUtils.class);

    /**
     * Convert file contents to POJO based on provided class
     */
    public static <T> T getFileContentsAsJSON(String pathAndFileName, Class<T> response) {

        String fileContents = getFileContents(pathAndFileName);
        if (fileContents == null) {
            return null;
        }

        return JsonUtils.getPOJOFromJSON(fileContents, response);
    }

    /**
     * Extract the file contents from the provided path and file name
     */
    public static String getFileContents(String pathAndFileName) {
        String fileContents = null;
        try {
            FileInputStream fileInputStream = new FileInputStream(pathAndFileName);
            fileContents = IOUtils.toString(fileInputStream, "UTF-8");
        } catch (IOException e) {
            LOG.error("Error reading file at {}", pathAndFileName, e);
        }

        return fileContents;
    }
}
