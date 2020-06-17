package org.opentripplanner.middleware;

import org.apache.commons.io.IOUtils;
import org.opentripplanner.middleware.utils.JsonUtils;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * File utility class for extracting and parsing file content
 */
public class FileUtils {

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
    private static String getFileContents(String pathAndFileName) {
        String fileContents = null;
        try {
            FileInputStream fileInputStream = new FileInputStream(pathAndFileName);
            fileContents = IOUtils.toString(fileInputStream, "UTF-8");
        } catch(IOException e) {

        }

        return fileContents;
    }
}
