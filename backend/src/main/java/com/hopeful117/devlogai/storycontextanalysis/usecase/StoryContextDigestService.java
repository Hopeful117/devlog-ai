package com.hopeful117.devlogai.storycontextanalysis.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Provides the canonical serialization and digest rules used by Story Context Agent contracts. */
@Service
@RequiredArgsConstructor
public class StoryContextDigestService {
    private final ObjectMapper objectMapper;

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to digest canonical value", exception);
        }
    }

    public String canonicalJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(entry -> quote(String.valueOf(entry.getKey())) + ":" + canonicalJson(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> values) {
            return StreamSupport.stream(values.spliterator(), false)
                    .map(this::canonicalJson)
                    .collect(Collectors.joining(",", "[", "]"));
        }
        if (value.getClass().isRecord()) {
            return canonicalJson(objectMapper.convertValue(value, Map.class));
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to canonicalize digest value", exception);
        }
    }

    private String quote(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
