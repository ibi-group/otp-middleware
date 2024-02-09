package org.opentripplanner.middleware.utils;

import org.opentripplanner.middleware.i18n.Message;

import java.util.Collection;
import java.util.Locale;

public class I18nUtils {
    /** Determines what collection size constitutes plural case. */
    public static boolean isPlural(Collection<?> collection) {
        return collection.size() > 1;
    }

    /** Introduces content with a label, typically using a colon. */
    public static String label(String labelText, String content, Locale locale) {
        // Trim any unused spaces from the formatted template if blank content is passed.
        // (Saves characters for size-limited content such as SMS and push notifications)
        return String.format(Message.LABEL_AND_CONTENT.get(locale), labelText, content).trim();
    }

    /** Used when the content is printed elsewhere. */
    public static String label(String labelText, Locale locale) {
        return label(labelText, "", locale);
    }
}
