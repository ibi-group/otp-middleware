package org.opentripplanner.middleware.models;

import java.util.ArrayList;
import java.util.List;

public class ApiUser extends AbstractUser {
    public List<String> apiKeyIds = new ArrayList<>();
}
