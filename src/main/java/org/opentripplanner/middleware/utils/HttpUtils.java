package org.opentripplanner.middleware.utils;

import org.eclipse.jetty.http.HttpStatus;
import spark.Request;

import static org.opentripplanner.middleware.utils.JsonUtils.logMessageAndHalt;

public class HttpUtils {

    /**
     * Get entity attribute value from request. If nulls are not allowed, halt with error message.
     */
    public static String getRequiredParamFromRequest(Request req, String paramName, boolean allowNull) {
        String paramValue = req.queryParams(paramName);
        if (paramValue == null && !allowNull) {
            logMessageAndHalt(req, HttpStatus.BAD_REQUEST_400, "The parameter name " + paramName + " must be provided.");
        }
        return paramValue;
    }
}
