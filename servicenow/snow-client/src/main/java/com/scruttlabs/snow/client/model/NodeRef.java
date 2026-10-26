package com.scruttlabs.snow.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A graph node, mirroring NodeRef in x_scopegraph src/server/lib/graph.ts.
 *
 * @param key         "&lt;table&gt;:&lt;sys_id&gt;" for a record, the bare table name for a table
 * @param kind        "record" or "table"
 * @param oob         table nodes only: false for custom (u_ / x_) tables
 * @param recordClass record nodes only: the concrete class, when the API sends it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NodeRef(String key, String kind, String table, String sysId, String name, Boolean oob,
                      @JsonProperty("class") String recordClass) {

    public boolean isTable() {
        return "table".equals(kind);
    }
}
