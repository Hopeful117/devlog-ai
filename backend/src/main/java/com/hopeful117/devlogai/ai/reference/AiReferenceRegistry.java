package com.hopeful117.devlogai.ai.reference;

import com.hopeful117.devlogai.projectcontext.EngineeringRelationship;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HexFormat;
import java.util.stream.Collectors;

public final class AiReferenceRegistry {
    private final List<AiReferenceBinding> bindings;
    private final Map<String, AiReferenceBinding> byIdentity;
    private final Set<AiReference> architectureKnowledgeReferences;
    private final String mappingDigest;

    public AiReferenceRegistry(List<AiReferenceBinding> bindings) {
        this(bindings, Set.of());
    }

    public AiReferenceRegistry(List<AiReferenceBinding> bindings,
            Set<AiReference> architectureKnowledgeReferences) {
        Map<String, AiReferenceBinding> unique = new LinkedHashMap<>();
        Map<String, AiReferenceBinding> byReference = new LinkedHashMap<>();
        for (AiReferenceBinding binding : bindings) {
            String key = key(binding.reference().type(), binding.reference().scope(),
                    binding.canonicalSourceIdentity());
            AiReferenceBinding previous = unique.putIfAbsent(key, binding);
            if (previous != null && !previous.equals(binding)) {
                throw new IllegalArgumentException("conflicting AI reference binding: " + key);
            }
            String referenceKey = key(binding.reference().type(), binding.reference().scope(),
                    binding.reference().ref());
            AiReferenceBinding previousReference = byReference.putIfAbsent(referenceKey, binding);
            if (previousReference != null && !previousReference.equals(binding)) {
                throw new IllegalArgumentException("conflicting AI reference token: " + referenceKey);
            }
        }
        this.bindings = List.copyOf(unique.values());
        this.byIdentity = Map.copyOf(unique);
        this.architectureKnowledgeReferences = Set.copyOf(architectureKnowledgeReferences);
        this.mappingDigest = digest(this.bindings);
    }

    public List<AiReferenceBinding> bindings() { return bindings; }
    public String mappingDigest() { return mappingDigest; }
    public Set<AiReference> architectureKnowledgeReferences() {
        return architectureKnowledgeReferences;
    }

    public Optional<AiReferenceBinding> find(AiReferenceType type, AiReferenceScope scope,
            String canonicalSourceIdentity) {
        return Optional.ofNullable(byIdentity.get(key(type, scope, canonicalSourceIdentity)));
    }

    public Optional<AiReference> referenceFor(AiReferenceType type, AiReferenceScope scope,
            String canonicalSourceIdentity) {
        return find(type, scope, canonicalSourceIdentity).map(AiReferenceBinding::reference);
    }

    public Optional<AiReference> referenceFor(EngineeringRelationship.Endpoint endpoint) {
        if (endpoint instanceof EngineeringRelationship.KnowledgeEndpoint knowledgeEndpoint) {
            AiReferenceType type = switch (knowledgeEndpoint.entityType()) {
                case INSIGHT -> AiReferenceType.INSIGHT;
                case ENGINEERING_EVENT -> AiReferenceType.ENGINEERING_EVENT;
                default -> null;
            };
            return type == null ? Optional.empty()
                    : referenceFor(type, AiReferenceScope.PROJECT, knowledgeEndpoint.entityId().toString());
        }
        return referenceFor(AiReferenceType.REPOSITORY_EVIDENCE, AiReferenceScope.REPOSITORY,
                endpoint.canonicalIdentity());
    }

    public List<AiReferenceBinding> groundingCandidates(String capability) {
        return bindings.stream()
                .filter(binding -> binding.groundingCapabilities().contains(capability))
                .toList();
    }

    private static String key(AiReferenceType type, AiReferenceScope scope, String identity) {
        return type + "\u0000" + scope + "\u0000" + identity;
    }

    private static String digest(List<AiReferenceBinding> values) {
        String canonical = values.stream()
                .map(AiReferenceRegistry::canonicalBinding)
                .sorted()
                .collect(java.util.stream.Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String canonicalBinding(AiReferenceBinding value) {
        return component(value.reference().type().name())
                + component(value.reference().ref())
                + component(value.reference().scope().name())
                + component(value.domainEntityType())
                + component(value.canonicalSourceIdentity())
                + component(value.groundingCapabilities().stream().sorted()
                        .map(AiReferenceRegistry::component)
                        .collect(Collectors.joining()));
    }

    private static String component(String value) {
        return value.length() + ":" + value;
    }
}
