package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        return IOUtils.toString(fileInputStream, StandardCharsets.UTF_8);
    }

    /**
     * Writes the file contents to a file and then writes that file to a zip file.
     * For info: https://stackoverflow.com/questions/14462371/preferred-way-to-use-java-zipoutputstream-and-bufferedoutputstream/17190212#17190212
     */
    public static void writeFileToZip(String zipPathAndFileName, String fileName, String contents) throws IOException {

        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipPathAndFileName)));
            zos.putNextEntry(new ZipEntry(fileName));
            zos.write(contents.getBytes());
            zos.closeEntry();
        } finally {
            try {
                if (zos != null) zos.close();
            } catch (IOException e) {
                LOG.error("Cannot close zip output stream.");
            }
        }
    }

    /**
     * Delete file on disk.
     */
    public static void deleteFile(File pathAndFileName) throws IOException {
        Files.deleteIfExists(pathAndFileName.toPath());
    }

    /**
     * Gets the operating system's temp directory.
     */
    public static File getTempDirectory() {
        return new File(System.getProperty("java.io.tmpdir"));
    }

}
