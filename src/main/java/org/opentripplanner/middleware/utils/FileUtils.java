package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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
     * Adds a single existing file to a new zip file. The contents of the file is streamed to avoid out-of-memory
     * errors.
     */
    public static void addSingleFileToZip(String pathToFile, String zipPathAndFileName) throws IOException {
        Path source = Paths.get(pathToFile);
        try (
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPathAndFileName));
            FileInputStream fis = new FileInputStream(source.toFile());
        ) {
            ZipEntry zipEntry = new ZipEntry(source.getFileName().toString());
            zos.putNextEntry(zipEntry);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
        }
    }

    /**
     * Writes content to a file. If the file does not exist it is created. The file contents will either be written over
     * or appended to depending on the append parameter.
     */
    public static void writeToFile(String pathAndFileName, boolean append, String contents) throws IOException {
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(pathAndFileName, append);
            fileWriter.write(contents);
        } finally {
            try {
                if (fileWriter != null) fileWriter.close();
            } catch (IOException e) {
                LOG.error("Cannot close file writer.", e);
            }
        }
    }

    /**
     * Delete file on disk.
     */
    public static void deleteFile(String pathAndFileName) throws IOException {
        Files.deleteIfExists(new File(pathAndFileName).toPath());
    }

    /**
     * Gets the operating system's temp directory.
     */
    public static File getTempDirectory() {
        return new File(System.getProperty("java.io.tmpdir"));
    }

    /**
     * Extracts the contents of a file contained within a zip file. This method will read the entire contents of a file
     * into memory and could cause an out-of-memory error if the file is too large.
     */
    public static String getContentsOfFileInZip(String zipFileNameAndPath, String fileName) throws IOException {
        String contents = null;
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(zipFileNameAndPath);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals(fileName)) {
                    InputStream stream = zipFile.getInputStream(entry);
                    contents = new String(stream.readAllBytes());
                    stream.close();
                }
            }
        } finally {
            if (zipFile != null) {
                zipFile.close();
            }
        }
        return contents;
    }
}
