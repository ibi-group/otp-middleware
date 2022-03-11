package org.opentripplanner.middleware.utils;

/**
 * Class that represents a CDP file available for download.
 */
public class CDPFile {
    /** The full S3 key AWS uses to identify the file within the bucket. */
    public String key;

    /** The human-readable name of the file. */
    public String name;

    /** The size in bytes of the file */
    public long size;

    public CDPFile(String key, String name, long size) {
        this.key = key;
        this.name = name;
        this.size = size;

    }
}
