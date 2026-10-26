package com.scruttlabs.snow.client.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JavaType;
import com.scruttlabs.snow.client.http.SnowHttpClient;
import com.scruttlabs.snow.client.model.DictionaryDto;
import com.scruttlabs.snow.client.model.Page;
import com.scruttlabs.snow.client.model.TableDto;

/**
 * Typed client for the x_1320561_scpgraph Scripted REST API ({@value #BASE}). Thread-safe.
 *
 * <p>Arguments are validated here with the same rules the instance applies (lib/rest.ts), so a bad table name
 * or limit fails fast without a round trip.
 */
public final class ScopeGraphApi {

    public static final String BASE = "/api/x_1320561_scpgraph/graph";
    /** The instance never returns more rows than this per call. */
    public static final int MAX_LIMIT = 100;

    private static final Pattern TABLE_NAME = Pattern.compile("^[a-z0-9_]{1,80}$");

    private final SnowHttpClient http;
    /** Generic types are erased at runtime, so Page&lt;TableDto&gt; is described to Jackson explicitly. */
    private final JavaType tablePage;
    private final JavaType fieldPage;

    public ScopeGraphApi(SnowHttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
        this.tablePage = http.types().constructParametricType(Page.class, TableDto.class);
        this.fieldPage = http.types().constructParametricType(Page.class, DictionaryDto.class);
    }

    /** {@code GET /tables?scope=&name_prefix=&extends=&offset=&limit=}, ordered by name. */
    public Page<TableDto> tables(TableQuery query, int offset, int limit) {
        Objects.requireNonNull(query, "query");
        if (query.extendsTable() != null && !query.extendsTable().isBlank()) tableName(query.extendsTable(), "extends");
        Map<String, String> params = query.toQueryParams();
        addPaging(params, offset, limit);
        return http.get(BASE + "/tables", params, tablePage);
    }

    /**
     * {@code GET /tables/{name}/fields?type=&offset=&limit=}: dictionary entries of one table, ordered by element.
     *
     * @param types internal types to keep, e.g. {"reference", "glide_list"}; empty keeps all
     */
    public Page<DictionaryDto> tableFields(String table, Set<String> types, int offset, int limit) {
        String name = tableName(table, "table");
        Objects.requireNonNull(types, "types");
        Map<String, String> params = new LinkedHashMap<>();
        // sorted so the same request always produces the same URL (helps logs and HTTP caches)
        params.put("type", String.join(",", new TreeSet<>(types)));
        addPaging(params, offset, limit);
        return http.get(BASE + "/tables/" + name + "/fields", params, fieldPage);
    }

    private static void addPaging(Map<String, String> params, int offset, int limit) {
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0, got " + offset);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ", got " + limit);
        }
        params.put("offset", Integer.toString(offset));
        params.put("limit", Integer.toString(limit));
    }

    private static String tableName(String value, String what) {
        if (value == null || !TABLE_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must be a table name ([a-z0-9_]{1,80}): " + value);
        }
        return value;
    }
}
