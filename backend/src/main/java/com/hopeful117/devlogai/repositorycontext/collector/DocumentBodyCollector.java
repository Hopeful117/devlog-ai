package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.collection.collector.SecureRepositoryContentReader;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.EngineeringStorySnapshot;
import com.hopeful117.devlogai.repositorycontext.AdrStatusParser;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.DocumentBudgetPolicy;
import com.hopeful117.devlogai.repositorycontext.DocumentPriorityComparator;
import com.hopeful117.devlogai.repositorycontext.DocumentCandidate;
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

    /**
     * Fallback timestamp when {@code Source.lastSynchronizedAt} is null
     * (source never collected). Ensures deterministic digests (Story 0119, Subtask 4).
     */
    static final Instant UNAVAILABLE_SYNC_TIMESTAMP = Instant.EPOCH;

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
    private final DocumentPriorityComparator priorityComparator;
    private final EvidenceFactory evidenceFactory;

    public DocumentBodyCollector(
            SecureRepositoryContentReader contentReader,
            SourceRepository sourceRepository,
            WorkspaceManager workspaceManager,
            EngineeringStoryRepository storyRepository,
            DocumentReferenceExtractor referenceExtractor,
            AdrStatusParser adrStatusParser,
            DocumentBudgetPolicy budgetPolicy,
            DocumentPriorityComparator priorityComparator,
            EvidenceFactory evidenceFactory
    ) {
        this.contentReader = contentReader;
        this.sourceRepository = sourceRepository;
        this.workspaceManager = workspaceManager;
        this.storyRepository = storyRepository;
        this.referenceExtractor = referenceExtractor;
        this.adrStatusParser = adrStatusParser;
        this.budgetPolicy = budgetPolicy;
        this.priorityComparator = priorityComparator;
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

        ResolvedWorkspace resolved = resolveWorkspace(scope);
        if (resolved == null) {
            log.warn("Document body collection skipped: workspace unavailable for source {}",
                    scope.sourceId());
            return List.of();
        }

        Instant occurredAt = resolved.occurredAt;

        // Build all document candidates with metadata for prioritization
        List<DocumentCandidate> docCandidates = new ArrayList<>();
        Set<String> seenReferences = new LinkedHashSet<>();

        // Build main story document candidate and extract references
        String storyPath = currentStory.storyPath();
        String storyContent = null;
        DocumentReferenceExtractor.ExtractedReferences refs = null;
        if (storyPath != null && !storyPath.isBlank()) {
            String normalizedPath = normalizePath(storyPath);
            DocumentReference docRef = new DocumentReference(scope.sourceId(), normalizedPath, scope.resolvedRevision());
            String evidenceRef = docRef.toEvidenceReference();
            if (seenReferences.add(evidenceRef)) {
                // Inline readStoryContent
                SecureRepositoryContentReader.ReadResult storyResult = contentReader.readComplete(
                        resolved.workspace, storyPath, budgetPolicy.maxCharactersPerDocument());
                storyContent = storyResult.text();
                refs = referenceExtractor.extract(storyContent);
                docCandidates.add(new DocumentCandidate(
                        "STORY_DOCUMENT",
                        normalizedPath,
                        buildStorySummary(currentStory),
                        RepositoryContextLayer.PROJECT_DOCUMENTATION,
                        evidenceRef,
                        occurredAt,
                        scope.sourceId().toString(),
                        normalizedPath,
                        null,
                        null,
                        0,
                        true,
                        null
                ));
            }
        }

        // If no story path, extract refs from empty content
        if (refs == null && storyContent != null) {
            refs = referenceExtractor.extract(storyContent);
        } else if (refs == null) {
            refs = referenceExtractor.extract("");
        }

        // Build ADR document candidates
        for (String adrNumber : refs.adrNumbers()) {
            String adrPath = resolveAdrPath(adrNumber);
            if (adrPath != null) {
                String normalizedPath = normalizePath(adrPath);
                DocumentReference docRef = new DocumentReference(scope.sourceId(), normalizedPath, scope.resolvedRevision());
                String evidenceRef = docRef.toEvidenceReference();
                if (seenReferences.add(evidenceRef)) {
                    // Read content to determine status for priority
                    SecureRepositoryContentReader.ReadResult adrResult = contentReader.readComplete(
                            resolved.workspace, adrPath, budgetPolicy.maxCharactersPerDocument());
                    DocumentStatus adrStatus = DocumentStatus.UNKNOWN;
                    if (adrResult.text() != null) {
                        AdrStatusParser.AdrStatusResult parsed = adrStatusParser.parse(adrResult.text());
                        adrStatus = parsed.status();
                    }
                    docCandidates.add(new DocumentCandidate(
                            "ADR_DOCUMENT",
                            normalizedPath,
                            "ADR-" + adrNumber,
                            RepositoryContextLayer.ADR,
                            evidenceRef,
                            occurredAt,
                            scope.sourceId().toString(),
                            normalizedPath,
                            adrNumber,
                            adrStatus,
                            0,
                            false,
                            adrStatus
                    ));
                }
            }
        }

        // Build referenced story document candidates
        UUID projectId = scope.projectId();
        for (String storyNumber : refs.storyNumbers()) {
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
            String refStoryPath = story.getStoryPath();
            if (refStoryPath == null || refStoryPath.isBlank()) {
                continue;
            }
            String normalizedPath = normalizePath(refStoryPath);
            DocumentReference docRef = new DocumentReference(scope.sourceId(), normalizedPath, scope.resolvedRevision());
            String evidenceRef = docRef.toEvidenceReference();
            if (seenReferences.add(evidenceRef)) {
                docCandidates.add(new DocumentCandidate(
                        "STORY_DOCUMENT",
                        normalizedPath,
                        "Story " + storyNumber,
                        RepositoryContextLayer.PROJECT_DOCUMENTATION,
                        evidenceRef,
                        occurredAt,
                        scope.sourceId().toString(),
                        normalizedPath,
                        null,
                        null,
                        num,
                        false,
                        null
                ));
            }
        }

        // Build roadmap document candidate
        if (refs.hasRoadmapReference()) {
            String normalizedPath = normalizePath("docs/roadmap.md");
            DocumentReference docRef = new DocumentReference(scope.sourceId(), normalizedPath, scope.resolvedRevision());
            String evidenceRef = docRef.toEvidenceReference();
            if (seenReferences.add(evidenceRef)) {
                docCandidates.add(new DocumentCandidate(
                        "ROADMAP_DOCUMENT",
                        normalizedPath,
                        "Roadmap",
                        RepositoryContextLayer.ROADMAP,
                        evidenceRef,
                        occurredAt,
                        scope.sourceId().toString(),
                        normalizedPath,
                        null,
                        null,
                        0,
                        false,
                        null
                ));
            }
        }

        // Sort by priority before budget application
        docCandidates.sort(priorityComparator);

        // Budget loop: deterministic enforcement per Story 0119 §5.2
        int remainingDocSlots = budgetPolicy.maxSelectedDocuments();
        int remainingAggregateBudget = budgetPolicy.maxTotalCharacters();
        List<RepositoryEvidence> selected = new ArrayList<>();

        for (DocumentCandidate candidate : docCandidates) {
            if (remainingDocSlots <= 0) break;
            if (remainingAggregateBudget <= 0) break;

            int effectiveMaxPerDoc = Math.min(budgetPolicy.maxCharactersPerDocument(), remainingAggregateBudget);

            SecureRepositoryContentReader.ReadResult result = contentReader.readComplete(
                    resolved.workspace, candidate.path(), effectiveMaxPerDoc);

            // Skip oversized documents entirely (SKIPPED / INPUT_TOO_LARGE)
            if (result.status() == SecureRepositoryContentReader.ReadResult.Status.SKIPPED) {
                log.warn("Document {} exceeds per-document limit, skipping", candidate.path());
                continue;
            }
            if (result.status() == SecureRepositoryContentReader.ReadResult.Status.UNAVAILABLE
                    || result.text() == null) {
                log.warn("Document {} is unavailable, excluding from evidence", candidate.path());
                continue;
            }

            RepositoryEvidenceContent.Status contentStatus = mapStatus(result.status());
            String contentText = result.text();
            String reason = result.reason();

            RepositoryEvidenceContent content = new RepositoryEvidenceContent(
                    contentStatus, contentText, reason,
                    DocumentBudgetPolicy.POLICY_ID, COLLECTOR_VERSION,
                    scope.resolvedRevision());

            // Determine layer and parse ADR status if needed
            RepositoryContextLayer layer = candidate.layer();
            DocumentStatus docStatus = DocumentStatus.UNKNOWN;
            String supersededBy = null;
            if (candidate.kind().equals("ADR_DOCUMENT") && contentText != null) {
                AdrStatusParser.AdrStatusResult parsed = adrStatusParser.parse(contentText);
                docStatus = parsed.status();
                supersededBy = parsed.supersededBy();
            }

            Map<String, String> extractionMeta = buildExtractionMeta(scope, candidate.path(), candidate.kind(),
                    docStatus, supersededBy);

            RepositoryEvidence evidence = evidenceFactory.create(
                    metadata(),
                    new EvidenceFactory.EvidenceInput(
                            candidate.layer(),
                            candidate.kind(),
                            candidate.evidenceRef(),
                            candidate.label(),
                            occurredAt,
                            List.of(),
                            scope.sourceId().toString(),
                            candidate.path(),
                            candidate.evidenceRef()),
                    budgetPolicy.maxCharactersPerDocument());

            evidence = evidence.withContent(content);
            evidence = evidence.withExtractionMetadata(extractionMeta);
            selected.add(evidence);

            // Charge actual retained content length
            remainingAggregateBudget -= contentText.length();
            remainingDocSlots--;
        }

        return List.copyOf(selected);
    }

    private static class ResolvedWorkspace {
        final SynchronizedWorkspace workspace;
        final Instant occurredAt;
        ResolvedWorkspace(SynchronizedWorkspace workspace, Instant occurredAt) {
            this.workspace = workspace;
            this.occurredAt = occurredAt;
        }
    }

    private ResolvedWorkspace resolveWorkspace(RepositoryRevisionScope scope) {
        try {
            Optional<Source> sourceOpt = sourceRepository.findById(scope.sourceId());
            if (sourceOpt.isEmpty()) {
                return null;
            }
            Source source = sourceOpt.get();
            SynchronizedWorkspace workspace = workspaceManager.synchronize(source, scope.resolvedRevision());
            Instant occurredAt = source.getLastSynchronizedAt() != null
                    ? source.getLastSynchronizedAt()
                    : UNAVAILABLE_SYNC_TIMESTAMP;
            return new ResolvedWorkspace(workspace, occurredAt);
        } catch (Exception e) {
            log.warn("Workspace resolution failed for source {}: {}", scope.sourceId(), e.getMessage());
            return null;
        }
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
