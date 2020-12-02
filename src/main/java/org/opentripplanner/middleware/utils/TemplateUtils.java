package org.opentripplanner.middleware.utils;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * A util class that helps with rendering templates
 */
public class TemplateUtils {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateUtils.class);

    private static final Configuration config = new Configuration(Configuration.VERSION_2_3_30);
    private static final List<String> sharedConfigKeys = List.of(
        "OTP_ADMIN_DASHBOARD_NAME", "OTP_ADMIN_DASHBOARD_URL", "OTP_UI_NAME", "OTP_UI_URL"
    );

    /**
     * Initializes the templating engine with various configurations. This must be called after
     * {@link ConfigUtils#loadConfig} is called.
     */
    public static void initialize() {
        try {
            config.setClassForTemplateLoading(TemplateUtils.class, "/templates/");
            config.setDefaultEncoding("UTF-8");
            for (String key : sharedConfigKeys) {
                config.setSharedVariable(key, ConfigUtils.getConfigPropertyAsText(key));
            }
        } catch (TemplateModelException e) {
            LOG.error("An error occurred while initializing FreeMarker: ", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Renders a template given an object.
     */
    public static String renderTemplateWithData(
        String templatePath,
        Object data
    ) throws IOException, TemplateException {
        StringWriter stringWriter = new StringWriter();
        config.getTemplate(templatePath).process(data, stringWriter);
        return stringWriter.toString();
    }
}
