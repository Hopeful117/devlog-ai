package com.hopeful117.devlogai.engineeringcontext;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** ADR-069 canonical, versioned serialization for the Core context identity. */
final class CanonicalContextDigest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CanonicalContextDigest() { }

    static String calculate(Object value) {
        try {
            String json = canonicalJson(value);
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) hex.append(String.format("%02x", current));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate canonical context digest", e);
        }
    }

    private static String canonicalJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(entry -> quote(String.valueOf(entry.getKey())) + ":" + canonicalJson(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> values) {
            return StreamSupport.stream(values.spliterator(), false)
                    .map(CanonicalContextDigest::canonicalJson)
                    .collect(Collectors.joining(",", "[", "]"));
        }
        if (value.getClass().isRecord()) {
            return canonicalJson(MAPPER.convertValue(value, Map.class));
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to canonicalize context value", e);
        }
    }

    private static String quote(String value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to canonicalize context key", e);
        }
    }
}
