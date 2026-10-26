package com.scruttlabs.snow.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Paging block of every list route.
 *
 * <p>{@code returned} can be lower than {@code limit} before the last page, because the instance drops rows the
 * caller cannot read. Page by {@link #nextOffset()} (or {@code total}), never by {@code returned}.
 *
 * @param nextOffset offset of the next page, or null on the last page
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageInfo(int offset, int limit, int returned, int total, Integer nextOffset) {

    public boolean hasNext() {
        return nextOffset != null;
    }
}
