package org.opentripplanner.middleware.utils;

import java.util.Collection;

public class I18nUtils {
    /** Determines what collection size constitutes plural case. */
    public static boolean isPlural(Collection<?> collection) {
        return collection.size() > 1;
    }
}
