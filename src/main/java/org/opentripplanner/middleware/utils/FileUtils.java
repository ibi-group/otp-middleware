package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * File utility class for extracting and parsing file content
 */
public class FileUtils {
    private static final Logger LOG = LoggerFactory.getLogger(FileUtils.class);

    /**
     * Convert file contents to POJO based on provided class
     */
    public static <T> T getFileContentsAsJSON(String pathAndFileName, Class<T> response) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new FileInputStream(pathAndFileName), response);
    }

    /**
     * Extract the file contents from the provided path and file name
     */
    public static String getFileContents(String pathAndFileName) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(pathAndFileName);
        return IOUtils.toString(fileInputStream, "UTF-8");
    }
}
