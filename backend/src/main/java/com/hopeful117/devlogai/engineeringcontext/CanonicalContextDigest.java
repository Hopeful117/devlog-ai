package com.hopeful117.devlogai.engineeringcontext;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** ADR-069 canonical JSON and SHA-256 identity helper for Core-owned contracts. */
public final class CanonicalContextDigest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CanonicalContextDigest() { }

    public static String calculate(Object value) {
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

    public static String canonicalJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(Normalizer.normalize(text, Normalizer.Form.NFC));
        if (value instanceof Number number) return normalizeNumber(number);
        if (value instanceof Map<?, ?> map) {
            java.util.List<Map.Entry<?, ?>> entries = new java.util.ArrayList<>(map.entrySet());
            java.util.Set<String> normalizedKeys = new java.util.HashSet<>();
            for (Map.Entry<?, ?> entry : entries) {
                String normalized = Normalizer.normalize(String.valueOf(entry.getKey()), Normalizer.Form.NFC);
                if (!normalizedKeys.add(normalized)) {
                    throw new IllegalArgumentException("canonical object contains colliding NFC keys: " + normalized);
                }
            }
            return entries.stream()
                    .sorted(Comparator.comparing(entry -> Normalizer.normalize(String.valueOf(entry.getKey()), Normalizer.Form.NFC)))
                    .map(entry -> quote(Normalizer.normalize(String.valueOf(entry.getKey()), Normalizer.Form.NFC)) + ":" + canonicalJson(entry.getValue(), String.valueOf(entry.getKey())))
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

    private static String canonicalJson(Object value, String field) {
        if (value instanceof String text && isPathField(field)) return quote(normalizePath(text));
        if (value instanceof Iterable<?> values && isPathField(field)) {
            return StreamSupport.stream(values.spliterator(), false)
                    .map(item -> canonicalJson(item, "path"))
                    .collect(Collectors.joining(",", "[", "]"));
        }
        return canonicalJson(value);
    }

    private static boolean isPathField(String field) {
        String key = field.toLowerCase(java.util.Locale.ROOT);
        return key.equals("path") || key.endsWith("path") || key.equals("file") || key.endsWith("file") || key.equals("resource") || key.equals("files");
    }

    private static String normalizePath(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC).replace("\\", "/");
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*")) throw new IllegalArgumentException("absolute paths are not canonical");
        java.util.ArrayDeque<String> parts = new java.util.ArrayDeque<>();
        for (String part : normalized.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) throw new IllegalArgumentException("path traversal is not canonical");
            else parts.addLast(part);
        }
        return String.join("/", parts);
    }

    private static String normalizeNumber(Number number) {
        if (number instanceof Double d && (!Double.isFinite(d))) throw new IllegalArgumentException("non-finite number");
        if (number instanceof Float f && (!Float.isFinite(f))) throw new IllegalArgumentException("non-finite number");
        BigDecimal decimal = new BigDecimal(number.toString()).stripTrailingZeros();
        return decimal.compareTo(BigDecimal.ZERO) == 0 ? "0" : decimal.toPlainString();
    }

    private static String quote(String value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to canonicalize context key", e);
        }
    }
}
