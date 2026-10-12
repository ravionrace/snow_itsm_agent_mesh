package com.acme.itsm.orchestrator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Secrets come from mounted files (Kubernetes / Docker secrets, Vault agent) in production, so they never
 * appear in environment listings or process arguments. A plain property value is the fallback for local use.
 */
public final class Secrets {

    private Secrets() {
    }

    public static String read(String file, String fallback) {
        if (file == null || file.isBlank()) {
            return fallback;
        }
        try {
            return Files.readString(Path.of(file), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read secret file " + file + ": " + e.getMessage(), e);
        }
    }
}
