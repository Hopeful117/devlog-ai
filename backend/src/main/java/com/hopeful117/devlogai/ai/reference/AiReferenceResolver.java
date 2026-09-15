package com.hopeful117.devlogai.ai.reference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable reverse view of one persisted execution mapping. */
public final class AiReferenceResolver {
    private final String contractVersion;
    private final String mappingDigest;
    private final Map<Key, AiReferenceMappingSnapshot.BindingSnapshot> byReference;

    private AiReferenceResolver(String contractVersion, String mappingDigest,
            Map<Key, AiReferenceMappingSnapshot.BindingSnapshot> byReference) {
        this.contractVersion = contractVersion;
        this.mappingDigest = mappingDigest;
        this.byReference = Map.copyOf(byReference);
    }

    public static AiReferenceResolver from(AiReferenceMappingSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<Key, AiReferenceMappingSnapshot.BindingSnapshot> reverse = new LinkedHashMap<>();
        for (var binding : snapshot.bindings()) {
            Key key = new Key(binding.type(), binding.ref(), binding.scope());
            var previous = reverse.putIfAbsent(key, binding);
            if (previous != null && !previous.equals(binding)) {
                throw failure("REFERENCE_MAPPING_FAILURE",
                        "Conflicting bindings claim " + key);
            }
        }
        return new AiReferenceResolver(snapshot.contractVersion(), snapshot.mappingDigest(), reverse);
    }

    @SuppressWarnings("unchecked")
    public static AiReferenceResolver fromMap(Map<String, Object> value) {
        if (value == null) {
            throw failure("REFERENCE_MAPPING_FAILURE", "AI reference mapping snapshot is missing");
        }
        String contractVersion = text(value, "contractVersion");
        String mappingDigest = text(value, "mappingDigest");
        Object rawBindings = value.get("bindings");
        if (!(rawBindings instanceof List<?> bindings)) {
            throw failure("REFERENCE_MAPPING_FAILURE", "AI reference mapping bindings are missing");
        }
        var snapshotBindings = new java.util.ArrayList<AiReferenceMappingSnapshot.BindingSnapshot>();
        for (Object raw : bindings) {
            if (!(raw instanceof Map<?, ?> rawMap)) {
                throw failure("REFERENCE_MAPPING_FAILURE", "AI reference mapping binding is malformed");
            }
            AiReferenceType type = enumValue(AiReferenceType.class, rawMap, "type");
            AiReferenceScope scope = enumValue(AiReferenceScope.class, rawMap, "scope");
            String ref = text(rawMap, "ref");
            String identity = text(rawMap, "canonicalSourceIdentity");
            Set<String> capabilities = stringSet(rawMap.get("groundingCapabilities"));
            snapshotBindings.add(new AiReferenceMappingSnapshot.BindingSnapshot(
                    type, ref, scope, identity, capabilities));
        }
        return from(new AiReferenceMappingSnapshot(contractVersion, mappingDigest, snapshotBindings));
    }

    public AiReferenceMappingSnapshot.BindingSnapshot resolve(AiReference reference) {
        Objects.requireNonNull(reference, "reference");
        var binding = byReference.get(new Key(reference.type(), reference.ref(), reference.scope()));
        if (binding == null) {
            boolean sameToken = byReference.keySet().stream()
                    .anyMatch(key -> key.ref().equals(reference.ref()));
            if (!sameToken) {
                throw failure("UNKNOWN_AI_REFERENCE", "Reference is not present in this task mapping");
            }
            boolean sameTokenAndType = byReference.keySet().stream()
                    .anyMatch(key -> key.ref().equals(reference.ref())
                            && key.type() == reference.type());
            if (!sameTokenAndType) {
                throw failure("REFERENCE_NAMESPACE_MISMATCH", "Reference namespace is not authorized");
            }
            throw failure("REFERENCE_SCOPE_MISMATCH", "Reference scope is not authorized");
        }
        return binding;
    }

    public AiReferenceMappingSnapshot.BindingSnapshot resolve(AiReference reference,
            String groundingCapability) {
        var binding = resolve(reference);
        if (groundingCapability != null
                && !binding.groundingCapabilities().contains(groundingCapability)) {
            throw failure("REFERENCE_NOT_ALLOWED_FOR_GROUNDING",
                    "Reference is not authorized for " + groundingCapability);
        }
        return binding;
    }

    public String contractVersion() { return contractVersion; }
    public String mappingDigest() { return mappingDigest; }

    private static String text(Map<?, ?> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof String text) || text.isBlank()) {
            throw failure("REFERENCE_MAPPING_FAILURE", "Mapping field " + key + " is invalid");
        }
        return text;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Map<?, ?> value, String key) {
        try {
            return Enum.valueOf(type, text(value, key));
        } catch (IllegalArgumentException exception) {
            throw failure("REFERENCE_MAPPING_FAILURE", "Mapping enum " + key + " is invalid");
        }
    }

    private static Set<String> stringSet(Object value) {
        if (!(value instanceof List<?> list)) {
            throw failure("REFERENCE_MAPPING_FAILURE", "Mapping grounding capabilities are invalid");
        }
        if (list.stream().anyMatch(item -> !(item instanceof String))) {
            throw failure("REFERENCE_MAPPING_FAILURE", "Mapping grounding capabilities are invalid");
        }
        return Set.copyOf((List<String>) list);
    }

    private static AiReferenceResolutionException failure(String code, String message) {
        return new AiReferenceResolutionException(code, message);
    }

    private record Key(AiReferenceType type, String ref, AiReferenceScope scope) { }
}
