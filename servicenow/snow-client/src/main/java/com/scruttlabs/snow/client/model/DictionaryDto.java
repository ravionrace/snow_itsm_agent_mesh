package com.scruttlabs.snow.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One item of {@code GET /tables/{name}/fields}: a sys_dictionary row (TableService.dictionaryJson in x_scopegraph).
 *
 * <p>{@code internal_type} and {@code reference} are reference fields in sys_dictionary, so they arrive as
 * {@link Ref} objects; use {@link #internalTypeName()} and {@link #referenceTable()} for their values.
 *
 * @param name    the table that defines the field
 * @param element the column name
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DictionaryDto(NodeRef node, String sysId, String name, String element, String columnLabel,
                            Ref internalType, Ref reference, String maxLength, Boolean mandatory, Boolean active,
                            Ref sysScope, Ref sysPackage) {

    /** e.g. "reference", "glide_list", "string". */
    public String internalTypeName() {
        return Ref.valueOf(internalType);
    }

    /** The table a reference / glide_list field points at, or null. */
    public String referenceTable() {
        String value = Ref.valueOf(reference);
        return value == null || value.isBlank() ? null : value;
    }
}
