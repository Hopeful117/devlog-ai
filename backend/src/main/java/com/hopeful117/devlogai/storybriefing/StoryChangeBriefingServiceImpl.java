package com.hopeful117.devlogai.storybriefing;

import com.hopeful117.devlogai.ai.reference.AiReference;
import com.hopeful117.devlogai.ai.reference.AiReferenceScope;
import com.hopeful117.devlogai.ai.reference.AiReferenceType;
import com.hopeful117.devlogai.engineeringcontext.CanonicalContextDigest;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.EngineeringContextFacade;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StoryChangeBriefingServiceImpl implements StoryChangeBriefingService {
    private static final String VERSION = "story-change-briefing-v1";
    private final EngineeringContextFacade contextFacade;

    public StoryChangeBriefingServiceImpl(EngineeringContextFacade contextFacade) {
        this.contextFacade = contextFacade;
    }

    @Override
    public StoryChangeBriefing build(String projectSlug, UUID storyId, String description) {
        if (storyId == null) throw new IllegalArgumentException("storyId is required");
        CanonicalEngineeringContext canonical = contextFacade.getCanonicalEngineeringContext(
                projectSlug, description == null ? "story-change-briefing" : description,
                List.of(), storyId);
        List<StoryChangeBriefing.Change> candidateChanges = canonical.repositoryContext() == null
                ? List.of() : canonical.repositoryContext().evidence().stream()
                .filter(this::isChangeEvidence).map(this::change).toList();
        List<String> warnings = warnings(canonical, candidateChanges);
        List<StoryChangeBriefing.Change> changes = validGitWindow(canonical)
                ? candidateChanges : List.of();
        Map<String, Object> snapshot = snapshot(canonical, storyId, changes, warnings);
        String text = descriptionFor(changes);
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("schema", "adr-069-story-change-briefing-projection-v1");
        projection.put("contractVersion", VERSION);
        projection.put("storyId", storyId);
        projection.put("contextDigest", canonical.contextDigest());
        projection.put("groundingStatus", "NOT_ESTABLISHED");
        projection.put("description", text);
        projection.put("changes", changes);
        projection.put("warnings", warnings);
        projection.put("snapshot", snapshot);
        String projectionDigest = CanonicalContextDigest.calculate(projection);
        return new StoryChangeBriefing(VERSION, storyId, canonical.contextDigest(),
                projectionDigest, "NOT_ESTABLISHED", text, changes, warnings, snapshot);
    }

    private String descriptionFor(List<StoryChangeBriefing.Change> changes) {
        return changes.isEmpty()
                ? "Aucune modification technique autorisée n'est établie dans la fenêtre Git."
                : changes.size() + " modification(s) technique(s) observée(s) dans la fenêtre Git."
                  + " Ce briefing décrit les éléments sans conclure sur leur cause ou leur intention.";
    }

    private Map<String, Object> snapshot(CanonicalEngineeringContext canonical, UUID storyId,
                                          List<StoryChangeBriefing.Change> changes, List<String> warnings) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("snapshotSchema", "adr-069-canonical-engineering-context-snapshot-v1");
        snapshot.put("contextVersion", canonical.contextVersion());
        snapshot.put("contextDigest", canonical.contextDigest());
        snapshot.put("storyId", storyId);
        snapshot.put("requestScope", canonical.requestEcho() == null
                ? Map.of("storyId", storyId) : canonical.requestEcho());
        snapshot.put("changeWindow", changeWindow(canonical));
        snapshot.put("freshness", canonical.freshness());
        snapshot.put("accounting", canonical.accounting());
        snapshot.put("diagnostics", canonical.diagnostics());
        snapshot.put("provenance", canonical.provenanceByReference());
        snapshot.put("trust", canonical.trustByReference());
        snapshot.put("typedReferenceMapping", canonical.authorizedReferences());
        snapshot.put("projectionPolicy", Map.of("contractVersion", VERSION,
                "serializer", "ADR-069-canonical-json-v1", "schema", "story-change-briefing-v1"));
        snapshot.put("changeCount", changes.size());
        snapshot.put("warnings", warnings);
        return snapshot;
    }

    private Map<String, Object> changeWindow(CanonicalEngineeringContext canonical) {
        Map<String, Object> window = new LinkedHashMap<>();
        Set<String> bases = new HashSet<>();
        Set<String> targets = new HashSet<>();
        if (canonical.repositoryContext() != null) canonical.repositoryContext().evidence().forEach(e -> {
            if (e.extractionMetadata() != null) {
                if (e.extractionMetadata().get("baseCommit") != null) bases.add(e.extractionMetadata().get("baseCommit"));
                if (e.extractionMetadata().get("targetCommit") != null) targets.add(e.extractionMetadata().get("targetCommit"));
            }
        });
        window.put("base", bases.size() == 1 ? bases.iterator().next() : null);
        window.put("target", targets.size() == 1 ? targets.iterator().next() : null);
        window.put("range", "(base,target]");
        return window;
    }

    private List<String> warnings(CanonicalEngineeringContext canonical, List<StoryChangeBriefing.Change> changes) {
        List<String> result = new ArrayList<>();
        if (canonical.repositoryContext() == null) result.add("STORY_SCOPE_UNAVAILABLE");
        else result.addAll(canonical.repositoryContext().warnings());
        Map<String, Object> window = changeWindow(canonical);
        String base = (String) window.get("base");
        String target = (String) window.get("target");
        if (base == null && target == null) result.add("STORY_SCOPE_UNAVAILABLE");
        else if (base == null || target == null) result.add("INCOMPLETE_COMMIT_GRAPH");
        else if (!isCommit(base) || !isCommit(target)) result.add("INVALID_GIT_BOUNDS");
        return result.stream().distinct().toList();
    }

    private boolean validGitWindow(CanonicalEngineeringContext canonical) {
        Map<String, Object> window = changeWindow(canonical);
        String base = (String) window.get("base");
        String target = (String) window.get("target");
        return base != null && target != null && isCommit(base) && isCommit(target);
    }

    private boolean isCommit(String value) {
        return value.matches("[0-9a-fA-F]{40}");
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
}
