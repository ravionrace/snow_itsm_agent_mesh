package com.acme.itsm.mcp.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * The exact record change a write will make, shown to the user before they click Confirm.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Preview(String action, String table, String record, List<FieldChange> changes, List<String> warnings) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldChange(String field, String from, String to) {
    }

    public static Builder of(String action, String table, String record) {
        return new Builder(action, table, record);
    }

    public static final class Builder {
        private final String action;
        private final String table;
        private final String record;
        private final List<FieldChange> changes = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        private Builder(String action, String table, String record) {
            this.action = action;
            this.table = table;
            this.record = record;
        }

        public Builder change(String field, String from, String to) {
            if (to != null) {
                changes.add(new FieldChange(field, from, to));
            }
            return this;
        }

        public Builder set(String field, String to) {
            return change(field, null, to);
        }

        public Builder warning(String warning) {
            warnings.add(warning);
            return this;
        }

        public Preview build() {
            return new Preview(action, table, record, List.copyOf(changes), warnings.isEmpty() ? null : List.copyOf(warnings));
        }
    }
}
