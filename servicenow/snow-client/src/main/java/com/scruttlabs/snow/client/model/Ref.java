package com.scruttlabs.snow.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A reference field as the ScopeGraph API serializes it: {@code {"value": sys_id or key, "display": ..., "table": ...}}.
 * A bare JSON string is accepted too and becomes a Ref with only {@code value}, so a field that is a plain string on
 * one instance and a reference on another still decodes.
 *
 * @param value   the stored value: usually a sys_id, a table name for sys_dictionary.reference, a type name for
 *                sys_dictionary.internal_type
 * @param display the display value, e.g. "Global"
 * @param table   the referenced table, e.g. "sys_scope"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Ref(String value, String display, String table) {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Ref(@JsonProperty("value") String value, @JsonProperty("display") String display,
               @JsonProperty("table") String table) {
        this.value = value;
        this.display = display;
        this.table = table;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Ref ofValue(String value) {
        return new Ref(value, null, null);
    }

    /** {@code ref.value()} or null when the reference itself is null. */
    public static String valueOf(Ref ref) {
        return ref == null ? null : ref.value();
    }

    /** {@code ref.display()} or null when the reference itself is null. */
    public static String displayOf(Ref ref) {
        return ref == null ? null : ref.display();
    }
}
