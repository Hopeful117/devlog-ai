package com.hopeful117.devlogai.storybriefing;

import tools.jackson.databind.ObjectMapper;
import com.hopeful117.devlogai.ai.reference.AiReference;
import com.hopeful117.devlogai.ai.reference.AiReferenceScope;
import com.hopeful117.devlogai.ai.reference.AiReferenceType;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.EngineeringContextFacade;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StoryChangeBriefingServiceImpl implements StoryChangeBriefingService {
    private static final String VERSION = "story-change-briefing-v1";
    private final EngineeringContextFacade contextFacade;
    private final ObjectMapper objectMapper;

    public StoryChangeBriefingServiceImpl(EngineeringContextFacade contextFacade,
                                          ObjectMapper objectMapper) {
        this.contextFacade = contextFacade;
        this.objectMapper = objectMapper;
    }

    @Override
    public StoryChangeBriefing build(String projectSlug, UUID storyId, String description) {
        if (storyId == null) throw new IllegalArgumentException("storyId is required");
        CanonicalEngineeringContext canonical = contextFacade.getCanonicalEngineeringContext(
                projectSlug, description == null ? "story-change-briefing" : description,
                List.of(), storyId);
        List<StoryChangeBriefing.Change> changes = canonical.repositoryContext() == null
                ? List.of() : canonical.repositoryContext().evidence().stream()
                .filter(this::isChangeEvidence).map(this::change).toList();
        List<String> warnings = canonical.repositoryContext() == null
                ? List.of("CANONICAL_CONTEXT_UNAVAILABLE")
                : canonical.repositoryContext().warnings();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contextVersion", canonical.contextVersion());
        snapshot.put("contextDigest", canonical.contextDigest());
        snapshot.put("storyId", storyId);
        snapshot.put("changeCount", changes.size());
        snapshot.put("warnings", warnings);
        String projectionDigest = digest(snapshot, changes);
        String text = changes.isEmpty()
                ? "Aucune modification technique autorisée n'est établie dans la fenêtre Git."
                : changes.size() + " modification(s) technique(s) observée(s) dans la fenêtre Git."
                  + " Ce briefing décrit les éléments sans conclure sur leur cause ou leur intention.";
        return new StoryChangeBriefing(VERSION, storyId, canonical.contextDigest(),
                projectionDigest, "NOT_ESTABLISHED", text, changes, warnings, snapshot);
    }

    private boolean isChangeEvidence(RepositoryEvidence evidence) {
        return evidence != null && evidence.reference() != null
                && (evidence.reference().startsWith("git:") || evidence.reference().startsWith("diff:"));
    }

    private StoryChangeBriefing.Change change(RepositoryEvidence evidence) {
        Map<String, String> provenance = new LinkedHashMap<>();
        if (evidence.provenance() != null) {
            if (evidence.provenance().repositoryLocation() != null)
                provenance.put("repositoryLocation", evidence.provenance().repositoryLocation());
            if (evidence.provenance().originatingFile() != null)
                provenance.put("originatingFile", evidence.provenance().originatingFile());
        }
        return new StoryChangeBriefing.Change(
                new AiReference(AiReferenceType.REPOSITORY_EVIDENCE, evidence.reference(),
                        AiReferenceScope.REPOSITORY), evidence.kind(), evidence.summary(),
                evidence.occurredAt() == null ? null : evidence.occurredAt().toString(), provenance);
    }

    private String digest(Map<String, Object> snapshot, List<StoryChangeBriefing.Change> changes) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(Map.of("version", VERSION,
                    "snapshot", snapshot, "changes", changes));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot digest briefing projection", exception);
        }
    }
}
