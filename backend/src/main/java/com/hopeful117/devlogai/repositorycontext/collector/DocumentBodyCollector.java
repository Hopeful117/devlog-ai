package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.collection.collector.SecureRepositoryContentReader;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.EngineeringStorySnapshot;
import com.hopeful117.devlogai.repositorycontext.AdrStatusParser;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.DocumentBudgetPolicy;
import com.hopeful117.devlogai.repositorycontext.DocumentReference;
import com.hopeful117.devlogai.repositorycontext.DocumentReferenceExtractor;
import com.hopeful117.devlogai.repositorycontext.DocumentStatus;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent;
import com.hopeful117.devlogai.repositorycontext.RepositoryRevisionScope;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Collects bounded, revision-pinned bodies of explicitly related
 * human-authored repository documents for SCA (ADR-063 §42, Story 0118).
 */
@Component
@Order(35)
public class DocumentBodyCollector implements RepositoryContextCollector {

    private static final Logger log = LoggerFactory.getLogger(DocumentBodyCollector.class);

    static final String COLLECTOR_ID = "document-body";
    static final String COLLECTOR_VERSION = "v1";
    static final String SOURCE_TYPE = "REPOSITORY_DOCUMENT";

    private final SecureRepositoryContentReader contentReader;
    private final SourceRepository sourceRepository;
    private final WorkspaceManager workspaceManager;
    private final EngineeringStoryRepository storyRepository;
    private final DocumentReferenceExtractor referenceExtractor;
    private final AdrStatusParser adrStatusParser;
    private final DocumentBudgetPolicy budgetPolicy;
    private final EvidenceFactory evidenceFactory;

    public DocumentBodyCollector(
            SecureRepositoryContentReader contentReader,
            SourceRepository sourceRepository,
            WorkspaceManager workspaceManager,
            EngineeringStoryRepository storyRepository,
            DocumentReferenceExtractor referenceExtractor,
            AdrStatusParser adrStatusParser,
            DocumentBudgetPolicy budgetPolicy,
            EvidenceFactory evidenceFactory
    ) {
        this.contentReader = contentReader;
        this.sourceRepository = sourceRepository;
        this.workspaceManager = workspaceManager;
        this.storyRepository = storyRepository;
        this.referenceExtractor = referenceExtractor;
        this.adrStatusParser = adrStatusParser;
        this.budgetPolicy = budgetPolicy;
        this.evidenceFactory = evidenceFactory;
    }

    @Override
    public String collectorId() { return COLLECTOR_ID; }

    @Override
    public String collectorVersion() { return COLLECTOR_VERSION; }

    @Override
    public List<RepositoryEvidence> collect(ContextRequest request) {
        RepositoryRevisionScope scope = request.revisionScope();
        if (scope == null) {
            return List.of();
        }
        var stories = request.analysisContext().engineeringStories();
        if (stories.isEmpty()) {
            return List.of();
        }
        var currentStory = stories.getFirst();

        SynchronizedWorkspace workspace = resolveWorkspace(scope);
        if (workspace == null) {
            log.warn("Document body collection skipped: workspace unavailable for source {}",
                    scope.sourceId());
            return List.of();
        }

        List<RepositoryEvidence> candidates = new ArrayList<>();
        Set<String> seenReferences = new LinkedHashSet<>();

        collectStoryDocument(currentStory, workspace, scope, candidates, seenReferences);

        String storyContent = readStoryContent(currentStory, workspace);
        DocumentReferenceExtractor.ExtractedReferences refs =
                referenceExtractor.extract(storyContent);

        collectAdrDocuments(refs.adrNumbers(), workspace, scope, candidates, seenReferences);
        collectReferencedStoryDocuments(refs.storyNumbers(), currentStory, workspace, scope,
                candidates, seenReferences);
        collectRoadmapDocument(refs.hasRoadmapReference(), workspace, scope, candidates,
                seenReferences);

        return List.copyOf(candidates);
    }

    private SynchronizedWorkspace resolveWorkspace(RepositoryRevisionScope scope) {
        try {
            Optional<Source> sourceOpt = sourceRepository.findById(scope.sourceId());
            if (sourceOpt.isEmpty()) {
                return null;
            }
            Source source = sourceOpt.get();
            return workspaceManager.synchronize(source, scope.resolvedRevision());
        } catch (Exception e) {
            log.warn("Workspace resolution failed for source {}: {}", scope.sourceId(), e.getMessage());
            return null;
        }
    }

    private void collectStoryDocument(
            EngineeringStorySnapshot storySnapshot,
            SynchronizedWorkspace workspace,
            RepositoryRevisionScope scope,
            List<RepositoryEvidence> candidates,
            Set<String> seenReferences
    ) {
        String storyPath = storySnapshot.storyPath();
        if (storyPath == null || storyPath.isBlank()) {
            return;
        }
        DocumentReference docRef = new DocumentReference(
                scope.sourceId(), normalizePath(storyPath), scope.resolvedRevision());
        String evidenceRef = docRef.toEvidenceReference();
        if (!seenReferences.add(evidenceRef)) {
            return;
        }

        SecureRepositoryContentReader.ReadResult result = contentReader.readComplete(
                workspace, storyPath, budgetPolicy.maxCharactersPerDocument());

        RepositoryEvidenceContent.Status contentStatus = mapStatus(result.status());
        String contentText = result.text();
        String reason = result.reason();

        RepositoryEvidenceContent content = new RepositoryEvidenceContent(
                contentStatus, contentText, reason,
                DocumentBudgetPolicy.POLICY_ID, COLLECTOR_VERSION,
                scope.resolvedRevision());

        String summary = buildStorySummary(storySnapshot);
        Map<String, String> extractionMeta = buildExtractionMeta(scope, storyPath,
                "STORY_DOCUMENT", null);

        RepositoryEvidence evidence = evidenceFactory.create(
                metadata(),
                new EvidenceFactory.EvidenceInput(
                        RepositoryContextLayer.PROJECT_DOCUMENTATION,
                        "STORY_DOCUMENT",
                        evidenceRef,
                        summary,
                        Instant.now(),
                        List.of(),
                        scope.sourceId().toString(),
                        storyPath,
                        storySnapshot.toString()),
                budgetPolicy.maxCharactersPerDocument());

        evidence = evidence.withContent(content);
        evidence = evidence.withExtractionMetadata(extractionMeta);
        candidates.add(evidence);
    }

    private String readStoryContent(EngineeringStorySnapshot storySnapshot, SynchronizedWorkspace workspace) {
        String path = storySnapshot.storyPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        SecureRepositoryContentReader.ReadResult result = contentReader.readComplete(
                workspace, path, budgetPolicy.maxCharactersPerDocument());
        return result.text();
    }

    private void collectAdrDocuments(
            Set<String> adrNumbers,
            SynchronizedWorkspace workspace,
            RepositoryRevisionScope scope,
            List<RepositoryEvidence> candidates,
            Set<String> seenReferences
    ) {
        for (String adrNumber : adrNumbers) {
            String adrPath = resolveAdrPath(adrNumber);
            if (adrPath == null) {
                continue;
            }
            collectDocumentBody(
                    adrPath, "ADR_DOCUMENT", RepositoryContextLayer.ADR,
                    "ADR-" + adrNumber, workspace, scope, candidates, seenReferences, true);
        }
    }

    private void collectReferencedStoryDocuments(
            Set<String> storyNumbers,
            Object currentStory,
            SynchronizedWorkspace workspace,
            RepositoryRevisionScope scope,
            List<RepositoryEvidence> candidates,
            Set<String> seenReferences
    ) {
        UUID projectId = scope.projectId();
        for (String storyNumber : storyNumbers) {
            Integer num;
            try {
                num = Integer.parseInt(storyNumber);
            } catch (NumberFormatException e) {
                continue;
            }
            Optional<EngineeringStory> storyOpt = storyRepository.findByProject_IdOrderByCreatedAtDesc(projectId)
                    .stream()
                    .filter(s -> num.equals(s.getStoryNumber()))
                    .findFirst();
            if (storyOpt.isEmpty()) {
                continue;
            }
            EngineeringStory story = storyOpt.get();
            String storyPath = story.getStoryPath();
            if (storyPath == null || storyPath.isBlank()) {
                continue;
            }
            collectDocumentBody(
                    storyPath, "STORY_DOCUMENT", RepositoryContextLayer.PROJECT_DOCUMENTATION,
                    "Story " + storyNumber, workspace, scope, candidates, seenReferences, false);
        }
    }

    private void collectRoadmapDocument(
            boolean hasRoadmapReference,
            SynchronizedWorkspace workspace,
            RepositoryRevisionScope scope,
            List<RepositoryEvidence> candidates,
            Set<String> seenReferences
    ) {
        if (!hasRoadmapReference) {
            return;
        }
        collectDocumentBody(
                "docs/roadmap.md", "ROADMAP_DOCUMENT", RepositoryContextLayer.ROADMAP,
                "Roadmap", workspace, scope, candidates, seenReferences, false);
    }

    private void collectDocumentBody(
            String relativePath,
            String kind,
            RepositoryContextLayer layer,
            String label,
            SynchronizedWorkspace workspace,
            RepositoryRevisionScope scope,
            List<RepositoryEvidence> candidates,
            Set<String> seenReferences,
            boolean parseAdrStatus
    ) {
        DocumentReference docRef = new DocumentReference(
                scope.sourceId(), normalizePath(relativePath), scope.resolvedRevision());
        String evidenceRef = docRef.toEvidenceReference();
        if (!seenReferences.add(evidenceRef)) {
            return;
        }

        SecureRepositoryContentReader.ReadResult result = contentReader.readComplete(
                workspace, relativePath, budgetPolicy.maxCharactersPerDocument());

        RepositoryEvidenceContent.Status contentStatus = mapStatus(result.status());
        String contentText = result.text();
        String reason = result.reason();

        RepositoryEvidenceContent content = new RepositoryEvidenceContent(
                contentStatus, contentText, reason,
                DocumentBudgetPolicy.POLICY_ID, COLLECTOR_VERSION,
                scope.resolvedRevision());

        DocumentStatus docStatus = DocumentStatus.UNKNOWN;
        String supersededBy = null;
        if (parseAdrStatus && contentText != null) {
            AdrStatusParser.AdrStatusResult parsed = adrStatusParser.parse(contentText);
            docStatus = parsed.status();
            supersededBy = parsed.supersededBy();
        }

        Map<String, String> extractionMeta = buildExtractionMeta(scope, relativePath, kind,
                docStatus, supersededBy);

        RepositoryEvidence evidence = evidenceFactory.create(
                metadata(),
                new EvidenceFactory.EvidenceInput(
                        layer,
                        kind,
                        evidenceRef,
                        label,
                        Instant.now(),
                        List.of(),
                        scope.sourceId().toString(),
                        relativePath,
                        evidenceRef),
                budgetPolicy.maxCharactersPerDocument());

        evidence = evidence.withContent(content);
        evidence = evidence.withExtractionMetadata(extractionMeta);
        candidates.add(evidence);
    }

    private String resolveAdrPath(String adrNumber) {
        String padded = adrNumber;
        while (padded.length() < 4) {
            padded = "0" + padded;
        }
        return "docs/decisions/ADR-" + padded + ".md";
    }

    private Map<String, String> buildExtractionMeta(
            RepositoryRevisionScope scope,
            String path,
            String kind,
            DocumentStatus status,
            String supersededBy
    ) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("resolvedRevision", scope.resolvedRevision());
        meta.put("documentKind", kind);
        meta.put("documentPath", path);
        if (status != null) {
            meta.put("documentStatus", status.name());
        }
        if (supersededBy != null) {
            meta.put("supersededBy", supersededBy);
        }
        return meta;
    }

    private Map<String, String> buildExtractionMeta(
            RepositoryRevisionScope scope,
            String path,
            String kind,
            DocumentStatus status
    ) {
        return buildExtractionMeta(scope, path, kind, status, null);
    }

    private RepositoryEvidenceContent.Status mapStatus(
            SecureRepositoryContentReader.ReadResult.Status readStatus) {
        return switch (readStatus) {
            case COMPLETE -> RepositoryEvidenceContent.Status.COMPLETE;
            case TRUNCATED -> RepositoryEvidenceContent.Status.TRUNCATED;
            case SKIPPED -> RepositoryEvidenceContent.Status.SKIPPED;
            case UNAVAILABLE -> RepositoryEvidenceContent.Status.UNAVAILABLE;
        };
    }

    private String normalizePath(String path) {
        if (path == null) return "";
        return path.replace('\\', '/').replaceAll("^/+", "");
    }

    private String buildStorySummary(EngineeringStorySnapshot storySnapshot) {
        String title = storySnapshot.title();
        String status = storySnapshot.status();
        return (title != null ? title : "Story") + (status != null ? " — " + status : "");
    }

    private EvidenceFactory.ContextRequestMetadata metadata() {
        return new EvidenceFactory.ContextRequestMetadata(
                collectorId(), collectorVersion(), SOURCE_TYPE);
    }
}
