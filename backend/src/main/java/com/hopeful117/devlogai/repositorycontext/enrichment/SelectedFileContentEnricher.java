package com.hopeful117.devlogai.repositorycontext.enrichment;

import com.hopeful117.devlogai.collection.collector.SecureRepositoryContentReader;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent;
import com.hopeful117.devlogai.repositorycontext.selection.EvidenceSelector;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class SelectedFileContentEnricher {
    private static final Set<String> ELIGIBLE_KINDS =
            Set.of("SOURCE_FILE", "TEST_FILE");
    private static final String LIMIT_WARNING =
            "CONTENT_ENRICHMENT_LIMIT_APPLIED";
    private static final String BUDGET_EXHAUSTED =
            "CONTENT_BUDGET_EXHAUSTED";

    private final RepositoryContentPolicy policy;
    private final SelectedContentAllocationPolicy allocationPolicy;
    private final SecureRepositoryContentReader reader;
    private final SourceRepository sourceRepository;
    private final WorkspaceManager workspaceManager;

    public SelectedFileContentEnricher(
            RepositoryContentPolicy policy,
            SelectedContentAllocationPolicy allocationPolicy,
            SecureRepositoryContentReader reader,
            SourceRepository sourceRepository,
            WorkspaceManager workspaceManager
    ) {
        this.policy = policy;
        this.allocationPolicy = allocationPolicy;
        this.reader = reader;
        this.sourceRepository = sourceRepository;
        this.workspaceManager = workspaceManager;
    }

    public EnrichmentResult enrich(
            ContextRequest request,
            EvidenceSelector.SelectionResult selection
    ) {
        Map<String, RepositoryEvidence> enriched = new HashMap<>();
        Map<String, Optional<SynchronizedWorkspace>> workspaces = new HashMap<>();
        LinkedHashSet<String> warnings = new LinkedHashSet<>();

        List<RepositoryEvidence> eligible = selection.selected().stream()
                .filter(value -> ELIGIBLE_KINDS.contains(value.kind()))
                .toList();
        List<SelectedContentAllocationPolicy.Allocation> allocations =
                allocationPolicy.allocate(eligible);
        int reservedMetadataTokens = allocations.stream()
                .mapToInt(this::metadataTokenDelta).sum();
        BudgetState state = new BudgetState(
                selection.usedTokens(), reservedMetadataTokens);

        for (SelectedContentAllocationPolicy.Allocation allocation : allocations) {
            RepositoryEvidence evidence = allocation.evidence();
            state.releaseMetadata(metadataTokenDelta(allocation));
            RepositoryEvidence updated = enrichEvidence(
                    allocation, request, state, workspaces);
            if (!state.fits(evidence, updated, request.budget().maximumTokens())) {
                updated = evidence;
                warnings.add("CONTENT_ALLOCATION_METADATA_BUDGET_EXHAUSTED");
            }
            enriched.put(evidence.reference(), updated);
            state.accountFor(evidence, updated);
            if (updated.content() != null) {
                addWarning(updated.content(), warnings);
            }
        }

        List<RepositoryEvidence> selected = selection.selected().stream()
                .map(value -> enriched.getOrDefault(value.reference(), value))
                .toList();
        Map<String, Integer> estimates = new HashMap<>();
        selected.forEach(value -> estimates.put(value.reference(), value.estimatedTokens()));
        List<RepositoryContext.SelectionDecision> decisions = selection.decisions().stream()
                .map(value -> new RepositoryContext.SelectionDecision(
                        value.evidenceReference(), value.selected(), value.reason(),
                        value.relevanceScore(), estimates.getOrDefault(
                                value.evidenceReference(), value.estimatedTokens())))
                .toList();
        int finalUsedTokens = selected.stream()
                .mapToInt(RepositoryEvidence::estimatedTokens).sum();
        return new EnrichmentResult(new EvidenceSelector.SelectionResult(
                selected, decisions, finalUsedTokens), List.copyOf(warnings));
    }

    private int metadataTokenDelta(
            SelectedContentAllocationPolicy.Allocation allocation
    ) {
        RepositoryEvidence evidence = allocation.evidence();
        String revision = evidence.extractionMetadata().get("resolvedRevision");
        RepositoryEvidence metadataOnly = evidence.withContent(content(
                RepositoryEvidenceContent.Status.SKIPPED, null,
                BUDGET_EXHAUSTED, revision, allocation));
        return Math.max(0,
                metadataOnly.estimatedTokens() - evidence.estimatedTokens());
    }

    private RepositoryEvidence enrichEvidence(
            SelectedContentAllocationPolicy.Allocation allocation,
            ContextRequest request,
            BudgetState state,
            Map<String, Optional<SynchronizedWorkspace>> workspaces
    ) {
        RepositoryEvidence evidence = allocation.evidence();
        String revision = evidence.extractionMetadata().get("resolvedRevision");
        if (state.enrichedFiles >= policy.getMaxEnrichedFiles()) {
            return evidence.withContent(content(RepositoryEvidenceContent.Status.SKIPPED,
                    null, "ENRICHED_FILE_LIMIT", revision, allocation));
        }
        int maximumCharacters = maximumCharacters(
                request, state, evidence, allocation, revision);
        if (maximumCharacters < 1) {
            return evidence.withContent(content(RepositoryEvidenceContent.Status.SKIPPED,
                    null, BUDGET_EXHAUSTED, revision, allocation));
        }
        Optional<SynchronizedWorkspace> workspace = workspace(
                evidence, request, revision, workspaces);
        if (workspace.isEmpty()) {
            return evidence.withContent(content(
                    RepositoryEvidenceContent.Status.UNAVAILABLE, null,
                    "WORKSPACE_UNAVAILABLE", revision, allocation));
        }
        SecureRepositoryContentReader.ReadResult result = reader.read(
                workspace.orElseThrow(), evidence.provenance().originatingFile(),
                maximumCharacters);
        RepositoryEvidence updated = evidence.withContent(content(status(result.status()),
                result.text(), result.reason(), revision, allocation));
        int allowedTokens = request.budget().maximumTokens()
                - state.usedTokens + evidence.estimatedTokens();
        if (updated.estimatedTokens() <= allowedTokens) return updated;
        return evidence.withContent(content(RepositoryEvidenceContent.Status.SKIPPED,
                null, BUDGET_EXHAUSTED, revision, allocation));
    }

    private int maximumCharacters(
            ContextRequest request,
            BudgetState state,
            RepositoryEvidence evidence,
            SelectedContentAllocationPolicy.Allocation allocation,
            String revision
    ) {
        int remainingCharacters = policy.getMaxTotalCharacters()
                - state.usedCharacters;
        int remainingTokens = request.budget().maximumTokens()
                - state.usedTokens - state.reservedMetadataTokens;
        int remainingFileSlots = Math.max(1,
                policy.getMaxEnrichedFiles() - state.enrichedFiles);
        RepositoryEvidence metadataOnly = evidence.withContent(content(
                RepositoryEvidenceContent.Status.SKIPPED, null,
                BUDGET_EXHAUSTED, revision, allocation));
        int metadataTokens = Math.max(0,
                metadataOnly.estimatedTokens() - evidence.estimatedTokens());
        int contentTokensPerSlot = Math.max(0,
                remainingTokens / remainingFileSlots - metadataTokens);
        return Math.min(policy.getMaxCharactersPerFile(),
                Math.min(remainingCharacters, contentTokensPerSlot * 4));
    }

    private void addWarning(
            RepositoryEvidenceContent value,
            LinkedHashSet<String> warnings
    ) {
        if ("ENRICHED_FILE_LIMIT".equals(value.reason())
                || BUDGET_EXHAUSTED.equals(value.reason())) {
            warnings.add(LIMIT_WARNING);
        }
        if (value.status() == RepositoryEvidenceContent.Status.TRUNCATED) {
            warnings.add("CONTENT_ENRICHMENT_TRUNCATED");
        } else if (value.status() == RepositoryEvidenceContent.Status.UNAVAILABLE) {
            warnings.add("CONTENT_ENRICHMENT_UNAVAILABLE");
        } else if (value.status() == RepositoryEvidenceContent.Status.SKIPPED) {
            warnings.add("CONTENT_ENRICHMENT_SKIPPED");
        }
    }

    private Optional<SynchronizedWorkspace> workspace(
            RepositoryEvidence evidence,
            ContextRequest request,
            String revision,
            Map<String, Optional<SynchronizedWorkspace>> workspaces
    ) {
        String sourceId = evidence.provenance().repositoryLocation();
        String key = sourceId + ":" + revision;
        return workspaces.computeIfAbsent(key, ignored -> {
            try {
                if (revision == null || revision.isBlank()) return Optional.empty();
                UUID id = UUID.fromString(sourceId);
                Optional<Source> found = sourceRepository
                        .findByIdAndProject_IdAndActiveTrue(id,
                                request.analysisContext().project().id());
                return found.map(source -> workspaceManager.synchronize(source, revision));
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        });
    }

    private RepositoryEvidenceContent content(
            RepositoryEvidenceContent.Status status,
            String text,
            String reason,
            String revision,
            SelectedContentAllocationPolicy.Allocation allocation
    ) {
        return new RepositoryEvidenceContent(status, text, reason,
                RepositoryContentPolicy.POLICY_ID,
                RepositoryContentPolicy.POLICY_VERSION, revision,
                SelectedContentAllocationPolicy.POLICY_ID,
                SelectedContentAllocationPolicy.POLICY_VERSION,
                allocation.rank(), allocation.reasons());
    }

    private RepositoryEvidenceContent.Status status(
            SecureRepositoryContentReader.ReadResult.Status status
    ) {
        return RepositoryEvidenceContent.Status.valueOf(status.name());
    }

    public record EnrichmentResult(
            EvidenceSelector.SelectionResult selection,
            List<String> warnings
    ) {
        public EnrichmentResult {
            warnings = List.copyOf(warnings);
        }
    }

    private static final class BudgetState {
        private int usedTokens;
        private int usedCharacters;
        private int enrichedFiles;
        private int reservedMetadataTokens;

        private BudgetState(int usedTokens, int reservedMetadataTokens) {
            this.usedTokens = usedTokens;
            this.reservedMetadataTokens = reservedMetadataTokens;
        }

        private void releaseMetadata(int tokens) {
            reservedMetadataTokens = Math.max(0,
                    reservedMetadataTokens - tokens);
        }

        private void accountFor(
                RepositoryEvidence original,
                RepositoryEvidence enriched
        ) {
            usedTokens += enriched.estimatedTokens() - original.estimatedTokens();
            if (enriched.content() != null && enriched.content().text() != null) {
                enrichedFiles++;
                usedCharacters += enriched.content().text().length();
            }
        }

        private boolean fits(
                RepositoryEvidence original,
                RepositoryEvidence enriched,
                int maximumTokens
        ) {
            return usedTokens + enriched.estimatedTokens()
                    - original.estimatedTokens() <= maximumTokens;
        }
    }
}
