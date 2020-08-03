package org.opentripplanner.middleware.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class YamlUtils {
    public static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
}
