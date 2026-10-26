package com.scruttlabs.snow.client.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Filters for {@code GET /tables}. Every filter is optional; null or blank means "no filter".
 *
 * @param scope        scope name ("global", "x_acme_app") or sys_scope sys_id
 * @param namePrefix   e.g. "u_"
 * @param extendsTable direct parent only, e.g. "task"
 */
public record TableQuery(String scope, String namePrefix, String extendsTable) {

    private static final TableQuery ALL = new TableQuery(null, null, null);

    public static TableQuery all() {
        return ALL;
    }

    public TableQuery withScope(String scope) {
        return new TableQuery(scope, namePrefix, extendsTable);
    }

    public TableQuery withNamePrefix(String namePrefix) {
        return new TableQuery(scope, namePrefix, extendsTable);
    }

    public TableQuery withExtends(String extendsTable) {
        return new TableQuery(scope, namePrefix, extendsTable);
    }

    /** The API's query parameters; blank filters are dropped later by the HTTP client. */
    Map<String, String> toQueryParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("scope", scope);
        params.put("name_prefix", namePrefix);
        params.put("extends", extendsTable);
        return params;
    }
}
