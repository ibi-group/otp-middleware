package org.opentripplanner.middleware.docs;

import io.github.manusant.ss.SwaggerHammer;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SwaggerHammerTest {
    /**
     * On the CI environment, the temp folder used by spark-swagger may be incorrectly
     * be set (e.g. /tmpswagger-ui instead of /tmp/swagger-ui).
     */
    @Test
    void ensureSwaggerUiPathFormat() {
        assertTrue(SwaggerHammer.getSwaggerUiFolder().endsWith(File.separator));
    }
}
