package org.opentripplanner.middleware.utils;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModelException;
import org.opentripplanner.middleware.bugsnag.BugsnagReporter;
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
    private static final String BASE_TEMPLATE_PATH = "/templates/";
    private static final List<String> sharedConfigKeys = List.of(
        "OTP_ADMIN_DASHBOARD_NAME", "OTP_ADMIN_DASHBOARD_URL", "OTP_UI_NAME", "OTP_UI_URL"
    );

    /**
     * Initializes the templating engine with various configurations. This must be called after
     * {@link ConfigUtils#loadConfig} is called.
     */
    public static void initialize() {
        try {
            config.setClassForTemplateLoading(TemplateUtils.class, BASE_TEMPLATE_PATH);
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
     * @param templatePath  path to template file (.ftl that is found in the {@link #BASE_TEMPLATE_PATH} resources
     *                      directory
     * @param templateData  template data (any kind of public class that has public getXxx/isXxx methods as
     *                      prescribed by the JavaBeans specification). This can also be a simple `Map<String, Object>`.
     * @return              generated text output
     */
    public static String renderTemplate(String templatePath, Object templateData) throws IOException, TemplateException {
        StringWriter stringWriter = new StringWriter();
        try {
            config.getTemplate(templatePath).process(templateData, stringWriter);
            return stringWriter.toString();
        } catch (TemplateException | IOException e) {
            BugsnagReporter.reportErrorToBugsnag("Failed to render template", templatePath, e);
            throw e;
        }
    }
}
