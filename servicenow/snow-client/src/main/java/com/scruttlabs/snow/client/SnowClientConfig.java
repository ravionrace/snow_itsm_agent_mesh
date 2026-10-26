package com.scruttlabs.snow.client;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Connection settings for one ServiceNow instance.
 *
 * @param instanceUrl    e.g. https://dev387916.service-now.com (a trailing slash is removed)
 * @param connectTimeout TCP connect timeout
 * @param requestTimeout time allowed for one request, response body included
 * @param maxRetries     retries after the first attempt for 429 / 5xx / I/O errors
 */
public record SnowClientConfig(URI instanceUrl, Duration connectTimeout, Duration requestTimeout, int maxRetries) {

    public SnowClientConfig {
        Objects.requireNonNull(instanceUrl, "instanceUrl");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        String url = instanceUrl.toString();
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new IllegalArgumentException("instanceUrl must be an http(s) URL: " + url);
        }
        instanceUrl = URI.create(url.replaceAll("/+$", ""));
        if (connectTimeout.isNegative() || connectTimeout.isZero()) throw new IllegalArgumentException("connectTimeout must be positive");
        if (requestTimeout.isNegative() || requestTimeout.isZero()) throw new IllegalArgumentException("requestTimeout must be positive");
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
    }

    public static SnowClientConfig of(String instanceUrl) {
        return new SnowClientConfig(URI.create(instanceUrl), Duration.ofSeconds(10), Duration.ofSeconds(60), 4);
    }

    /** instance URL + path (+ "?" + raw query when not empty). */
    public URI resolve(String path, String rawQuery) {
        return URI.create(instanceUrl + path + (rawQuery == null || rawQuery.isEmpty() ? "" : "?" + rawQuery));
    }
}
