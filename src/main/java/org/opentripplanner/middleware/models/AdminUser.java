package org.opentripplanner.middleware.models;

import org.opentripplanner.middleware.auth.Permission;

import java.util.HashMap;
import java.util.Map;

public class AdminUser extends User {
    public Map<String, Permission> permissions = new HashMap<>();
}
