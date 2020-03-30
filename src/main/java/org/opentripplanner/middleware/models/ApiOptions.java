package org.opentripplanner.middleware.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ApiOptions implements Serializable {
    private static final long serialVersionUID = 1L;
    public List<String> apiKeyIds = new ArrayList<>();
}
