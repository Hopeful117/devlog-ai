package com.hopeful117.devlogai.repositorycontext.enrichment;

import com.hopeful117.devlogai.collection.collector.SecureRepositoryContentReader;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols;
import com.hopeful117.devlogai.repositorycontext.selection.EvidenceSelector;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class SelectedJavaSymbolEnricher {
    private final RepositorySymbolPolicy policy;
    private final SelectedSymbolAllocationPolicy allocationPolicy;
    private final JavaDeclarationExtractor extractor;
    private final SecureRepositoryContentReader reader;
    private final SourceRepository sourceRepository;
    private final WorkspaceManager workspaceManager;

    public SelectedJavaSymbolEnricher(
            RepositorySymbolPolicy policy,
            SelectedSymbolAllocationPolicy allocationPolicy,
            JavaDeclarationExtractor extractor,
            SecureRepositoryContentReader reader,
            SourceRepository sourceRepository,
            WorkspaceManager workspaceManager
    ) {
        this.policy = policy;
        this.allocationPolicy = allocationPolicy;
        this.extractor = extractor;
        this.reader = reader;
        this.sourceRepository = sourceRepository;
        this.workspaceManager = workspaceManager;
    }

    public EnrichmentResult enrich(
            ContextRequest request,
            EvidenceSelector.SelectionResult selection
    ) {
        List<RepositoryEvidence> eligible = selection.selected().stream()
                .filter(this::eligible).toList();
        List<SelectedSymbolAllocationPolicy.Allocation> allocations =
                allocationPolicy.allocate(eligible);
        Map<String, RepositoryEvidence> enriched = new HashMap<>();
        Map<String, Optional<SynchronizedWorkspace>> workspaces = new HashMap<>();
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        Map<String, RepositoryEvidence> fallbacks = new HashMap<>();
        List<SelectedSymbolAllocationPolicy.Allocation> considered = new java.util.ArrayList<>();
        int reservedMetadataTokens = 0;
        List<SelectedSymbolAllocationPolicy.Allocation> fileBounded = allocations.stream()
                .limit(policy.getMaxInspectedFiles()).toList();
        if (fileBounded.size() < allocations.size())
            warnings.add("SYMBOL_ENRICHMENT_LIMIT_APPLIED");
        for (SelectedSymbolAllocationPolicy.Allocation allocation : fileBounded) {
            RepositoryEvidence original = allocation.evidence();
            RepositoryEvidence fallback = original.withSymbols(fallback(original, allocation));
            int metadataTokens = fallback.estimatedTokens() - original.estimatedTokens();
            if (reservedMetadataTokens + metadataTokens > policy.getMaxTokens()
                    || selection.usedTokens() + reservedMetadataTokens + metadataTokens
                    > request.budget().maximumTokens()) {
                warnings.add("SYMBOL_ENRICHMENT_METADATA_BUDGET_EXHAUSTED");
                break;
            }
            considered.add(allocation);
            fallbacks.put(original.reference(), fallback);
            reservedMetadataTokens += metadataTokens;
        }
        int usedTokens = selection.usedTokens() + reservedMetadataTokens;
        int symbolTokens = reservedMetadataTokens;
        int totalSymbols = 0;
        long started = System.nanoTime();

        for (SelectedSymbolAllocationPolicy.Allocation allocation : considered) {
            RepositoryEvidence original = allocation.evidence();
            RepositoryEvidence fallback = fallbacks.get(original.reference());
            RepositoryEvidenceSymbols symbols;
            if (elapsed(started).compareTo(policy.getMaxTotalDuration()) >= 0) {
                symbols = result(RepositoryEvidenceSymbols.Status.SKIPPED,
                        "SYMBOL_TOTAL_DURATION_LIMIT", original, allocation,
                        OutcomeDetails.empty());
                warnings.add("SYMBOL_ENRICHMENT_DURATION_LIMIT_APPLIED");
            } else {
                symbols = inspect(request, original, allocation, workspaces,
                        policy.getMaxTotalSymbols() - totalSymbols);
                addWarning(symbols, warnings);
            }
            RepositoryEvidence updated = original.withSymbols(symbols);
            int replacementDelta = updated.estimatedTokens()
                    - fallback.estimatedTokens();
            if (symbolTokens + replacementDelta > policy.getMaxTokens()
                    || usedTokens + replacementDelta > request.budget().maximumTokens()) {
                updated = fallback;
                replacementDelta = 0;
                warnings.add("SYMBOL_ENRICHMENT_TOKEN_LIMIT_APPLIED");
            }
            enriched.put(original.reference(), updated);
            usedTokens += replacementDelta;
            symbolTokens += replacementDelta;
            totalSymbols += updated.symbols().returnedSymbolCount();
        }

        List<RepositoryEvidence> selected = selection.selected().stream()
                .map(value -> enriched.getOrDefault(value.reference(), value)).toList();
        Map<String, Integer> estimates = new HashMap<>();
        selected.forEach(value -> estimates.put(value.reference(), value.estimatedTokens()));
        List<RepositoryContext.SelectionDecision> decisions = selection.decisions().stream()
                .map(value -> new RepositoryContext.SelectionDecision(
                        value.evidenceReference(), value.selected(), value.reason(),
                        value.relevanceScore(), estimates.getOrDefault(
                                value.evidenceReference(), value.estimatedTokens())))
                .toList();
        int finalTokens = selected.stream().mapToInt(RepositoryEvidence::estimatedTokens).sum();
        return new EnrichmentResult(new EvidenceSelector.SelectionResult(
                selected, decisions, finalTokens), List.copyOf(warnings));
    }

    private RepositoryEvidenceSymbols fallback(
            RepositoryEvidence evidence,
            SelectedSymbolAllocationPolicy.Allocation allocation
    ) {
        return new RepositoryEvidenceSymbols(RepositoryEvidenceSymbols.Status.SKIPPED,
                "SYMBOL_TOKEN_LIMIT", RepositorySymbolPolicy.POLICY_ID,
                RepositorySymbolPolicy.POLICY_VERSION,
                JavaDeclarationExtractor.EXTRACTOR_ID,
                JavaDeclarationExtractor.EXTRACTOR_VERSION, revision(evidence),
                allocation.rank(), List.of("ALLOCATION_RANK=" + allocation.rank()),
                false, 0, null, List.of());
    }

    private RepositoryEvidenceSymbols inspect(
            ContextRequest request,
            RepositoryEvidence evidence,
            SelectedSymbolAllocationPolicy.Allocation allocation,
            Map<String, Optional<SynchronizedWorkspace>> workspaces,
            int remainingSymbols
    ) {
        if (remainingSymbols < 1) {
            return result(RepositoryEvidenceSymbols.Status.SKIPPED,
                    "SYMBOL_COUNT_LIMIT", evidence, allocation, OutcomeDetails.empty());
        }
        String revision = revision(evidence);
        Optional<SynchronizedWorkspace> workspace = workspace(
                evidence, request, revision, workspaces);
        if (workspace.isEmpty()) {
            return result(RepositoryEvidenceSymbols.Status.UNAVAILABLE,
                    "WORKSPACE_UNAVAILABLE", evidence, allocation, OutcomeDetails.empty());
        }
        SecureRepositoryContentReader.ReadResult read = reader.readComplete(
                workspace.orElseThrow(), evidence.provenance().originatingFile(),
                policy.getMaxInputCharactersPerFile());
        if (read.status() != SecureRepositoryContentReader.ReadResult.Status.COMPLETE) {
            RepositoryEvidenceSymbols.Status status =
                    read.status() == SecureRepositoryContentReader.ReadResult.Status.UNAVAILABLE
                            ? RepositoryEvidenceSymbols.Status.UNAVAILABLE
                            : RepositoryEvidenceSymbols.Status.SKIPPED;
            return result(status, read.reason(), evidence, allocation,
                    OutcomeDetails.empty());
        }
        JavaDeclarationExtractor.Extraction extraction = extractWithinDeadline(read.text());
        List<RepositoryEvidenceSymbols.JavaDeclaration> declarations =
                extraction.declarations().stream().limit(remainingSymbols).toList();
        boolean truncated = extraction.truncated()
                || declarations.size() < extraction.declarations().size();
        RepositoryEvidenceSymbols.Status status = switch (extraction.outcome()) {
            case EXTRACTED -> RepositoryEvidenceSymbols.Status.EXTRACTED;
            case NO_SYMBOLS -> RepositoryEvidenceSymbols.Status.NO_SUPPORTED_SYMBOLS;
            case UNSUPPORTED -> RepositoryEvidenceSymbols.Status.UNSUPPORTED;
            case FAILED -> RepositoryEvidenceSymbols.Status.FAILED;
        };
        return result(status, extraction.reason(), evidence, allocation,
                new OutcomeDetails(truncated, declarations.size(),
                        extraction.availableCount(), declarations));
    }

    private JavaDeclarationExtractor.Extraction extractWithinDeadline(String source) {
        CompletableFuture<JavaDeclarationExtractor.Extraction> future =
                new CompletableFuture<>();
        Thread worker = Thread.startVirtualThread(() -> {
            try {
                future.complete(extractor.extract(source, policy));
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        });
        try {
            return future.get(policy.getMaxParseDurationPerFile().toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            return new JavaDeclarationExtractor.Extraction(
                    JavaDeclarationExtractor.Outcome.FAILED, List.of(), 0,
                    false, "PARSE_TIMEOUT");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new JavaDeclarationExtractor.Extraction(
                    JavaDeclarationExtractor.Outcome.FAILED, List.of(), 0,
                    false, "PARSE_INTERRUPTED");
        } catch (ExecutionException exception) {
            return new JavaDeclarationExtractor.Extraction(
                    JavaDeclarationExtractor.Outcome.FAILED, List.of(), 0,
                    false, "EXTRACTION_FAILURE");
        } finally {
            future.cancel(true);
            worker.interrupt();
        }
    }

    private Optional<SynchronizedWorkspace> workspace(
            RepositoryEvidence evidence,
            ContextRequest request,
            String revision,
            Map<String, Optional<SynchronizedWorkspace>> workspaces
    ) {
        String sourceId = evidence.provenance().repositoryLocation();
        return workspaces.computeIfAbsent(sourceId + ":" + revision, ignored -> {
            try {
                if (revision == null || revision.isBlank()) return Optional.empty();
                UUID id = UUID.fromString(sourceId);
                Optional<Source> source = sourceRepository
                        .findByIdAndProject_IdAndActiveTrue(id,
                                request.analysisContext().project().id());
                return source.map(value -> workspaceManager.synchronize(value, revision));
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        });
    }

    private RepositoryEvidenceSymbols result(
            RepositoryEvidenceSymbols.Status status,
            String reason,
            RepositoryEvidence evidence,
            SelectedSymbolAllocationPolicy.Allocation allocation,
            OutcomeDetails details
    ) {
        return new RepositoryEvidenceSymbols(status, reason,
                RepositorySymbolPolicy.POLICY_ID, RepositorySymbolPolicy.POLICY_VERSION,
                JavaDeclarationExtractor.EXTRACTOR_ID,
                JavaDeclarationExtractor.EXTRACTOR_VERSION, revision(evidence),
                allocation.rank(), allocation.reasons(), details.truncated(),
                details.returned(), details.available(), details.declarations());
    }

    private void addWarning(
            RepositoryEvidenceSymbols symbols,
            LinkedHashSet<String> warnings
    ) {
        if (symbols.truncated()) warnings.add("SYMBOL_ENRICHMENT_TRUNCATED");
        if (symbols.status() == RepositoryEvidenceSymbols.Status.UNSUPPORTED)
            warnings.add("SYMBOL_ENRICHMENT_UNSUPPORTED");
        if (symbols.status() == RepositoryEvidenceSymbols.Status.UNAVAILABLE)
            warnings.add("SYMBOL_ENRICHMENT_UNAVAILABLE");
        if (symbols.status() == RepositoryEvidenceSymbols.Status.FAILED)
            warnings.add("SYMBOL_ENRICHMENT_FAILED");
        if (symbols.status() == RepositoryEvidenceSymbols.Status.SKIPPED)
            warnings.add("SYMBOL_ENRICHMENT_SKIPPED");
    }

    private boolean eligible(RepositoryEvidence value) {
        return ("SOURCE_FILE".equals(value.kind()) || "TEST_FILE".equals(value.kind()))
                && value.provenance() != null
                && value.provenance().originatingFile() != null
                && value.provenance().originatingFile().endsWith(".java");
    }

    private String revision(RepositoryEvidence evidence) {
        return evidence.extractionMetadata().get("resolvedRevision");
    }

    private Duration elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }

    public record EnrichmentResult(
            EvidenceSelector.SelectionResult selection,
            List<String> warnings
    ) {
        public EnrichmentResult { warnings = List.copyOf(warnings); }
    }

    private record OutcomeDetails(
            boolean truncated,
            int returned,
            Integer available,
            List<RepositoryEvidenceSymbols.JavaDeclaration> declarations
    ) {
        private static OutcomeDetails empty() {
            return new OutcomeDetails(false, 0, null, List.of());
        }
    }
}
