package org.opentripplanner.middleware.otp.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntUtils {
    private static final Logger LOG = LoggerFactory.getLogger(IntUtils.class);

    public static int getIntFromString(String input) {
        int retVal = 0;

        Integer n = getIntegerFromString(input);
        if (n != null) {
            retVal = n.intValue();
        } else if (input != null) {
            retVal = input.hashCode();
        }
        return retVal;
    }

    public static Integer getIntegerFromString(String input) {
        try {
            return Integer.parseInt(input);
        } catch (Exception e) {
            try {
                String in = input.replaceAll("\\D", "");
                return Integer.parseInt(in);
            } catch (Exception ee) {
                return null;
            }
        }
    }

    public static long getLongFromString(String input) {
        return getLongFromString(input, -111);
    }

    public static long getLongFromString(String input, long def) {
        try {
            return Long.valueOf(input);
        } catch (Exception e) {
            LOG.info("Not a big deal...going to return default value", e);
            return def;
        }
    }
}
