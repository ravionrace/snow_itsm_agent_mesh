package com.scruttlabs.snow.client.model;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One page of a list route: {@code {"items": [...], "page": {...}}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Page<T>(List<T> items, PageInfo page) {

    public Page {
        items = items == null ? List.of() : List.copyOf(items);
        Objects.requireNonNull(page, "page");
    }
}
