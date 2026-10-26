package com.scruttlabs.snow.client.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One item of {@code GET /tables}: a sys_db_object row (TableService.tableJson in x_scopegraph).
 *
 * @param superClassName the parent table's name, or null for a base table
 * @param sysUpdatedOn   raw glide_date_time, e.g. "2025-03-01 10:00:00 UTC"; see {@link #updatedAt()}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TableDto(NodeRef node, String sysId, String name, String label,
                       Ref superClass, String superClassName, Ref sysScope, Ref sysPackage,
                       @JsonProperty("is_extendable") boolean isExtendable, String sysUpdatedOn) {

    public Instant updatedAt() {
        return SnowTime.parse(sysUpdatedOn);
    }

    /** Custom table (u_ / x_). Uses the API's node.oob when present. */
    public boolean isOob() {
        if (node != null && node.oob() != null) return node.oob();
        return name == null || !(name.startsWith("u_") || name.startsWith("x_"));
    }
}
