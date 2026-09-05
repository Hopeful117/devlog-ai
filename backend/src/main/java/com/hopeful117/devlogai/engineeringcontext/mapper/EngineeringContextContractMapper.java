package com.hopeful117.devlogai.engineeringcontext.mapper;

import com.hopeful117.devlogai.contracts.engineeringcontext.DevlogResourceUriFactory;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextFreshness;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextMetadata;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidence;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidenceContent;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidenceSymbols;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringSymbolDeclaration;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringSymbolLocation;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringSymbolParameter;
import com.hopeful117.devlogai.contracts.engineeringcontext.ContextSection;
import com.hopeful117.devlogai.contracts.engineeringcontext.ContextRequestEcho;
import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.mapper.ProjectContextContractMapper;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EngineeringContextContractMapper {

    private static final Pattern GIT_COMMIT_REFERENCE =
            Pattern.compile("^git:[0-9a-fA-F\\-]+:([0-9a-fA-F]{40}|[0-9a-fA-F]{64})$");

    private static final String WARNING_PROJECT_CONTEXT_STALE = "PROJECT_CONTEXT_STALE";
    private static final String WARNING_PROJECT_CONTEXT_PARTIALLY_FRESH =
            "PROJECT_CONTEXT_PARTIALLY_FRESH";

    private final ProjectContextContractMapper projectContextContractMapper;

    public EngineeringContext toContract(
            ProjectContextSnapshot projectContext,
            RepositoryContext repositoryContext,
            String intent,
            List<String> files,
            UUID storyId,
            ProjectFreshnessSummary freshnessSummary
    ) {
        String projectSlug = projectContext.project().slug();

        // Map all evidence with trust tier classification
        List<EngineeringEvidence> allEvidence = repositoryContext.evidence().stream()
                .map(evidence -> mapEvidence(evidence, repositoryContext, projectSlug))
                .filter(e -> e != null) // Filter out EXCLUDED
                .toList();

        // Apply scope hints filtering
        List<EngineeringEvidence> filteredEvidence = applyScopeFilters(allEvidence, files, storyId, projectContext);

        // Partition into sections by trust tier with deterministic ordering
        Map<TrustTier, List<EngineeringEvidence>> evidenceByTier = filteredEvidence.stream()
                .collect(Collectors.groupingBy(EngineeringEvidence::trustTier));

        // Build sections in trust-tier order
        List<ContextSection> sections = buildSections(evidenceByTier);

        // Build compatibility evidence[] by flattening sections in trust-tier order
        List<EngineeringEvidence> compatibilityEvidence = sections.stream()
                .flatMap(s -> s.evidence().stream())
                .toList();

        // Build request echo with normalized scope
        ContextRequestEcho requestEcho = new ContextRequestEcho(
                projectSlug,
                intent,
                files != null ? files : List.of(),
                storyId
        );

        return new EngineeringContext(
                projectContextContractMapper.toContract(projectContext),
                intent,
                compatibilityEvidence,
                mapMetadata(repositoryContext, freshnessSummary),
                sections,
                requestEcho
        );
    }

    private List<EngineeringEvidence> applyScopeFilters(
            List<EngineeringEvidence> evidence,
            List<String> files,
            UUID storyId,
            ProjectContextSnapshot projectContext
    ) {
        List<EngineeringEvidence> filtered = new ArrayList<>(evidence);

        // Apply files[] filter - only to TECHNICAL_EVIDENCE section
        if (files != null && !files.isEmpty()) {
            filtered = filtered.stream()
                    .filter(e -> {
                        if (e.trustTier() != TrustTier.TECHNICAL_EVIDENCE) return true;
                        String originatingFile = e.originatingFile();
                        if (originatingFile == null) return false;
                        return files.stream().anyMatch(f ->
                                originatingFile.equals(f) || originatingFile.startsWith(f + "/"));
                    })
                    .collect(Collectors.toList());
        }

        // Apply storyId filter - only to TECHNICAL_EVIDENCE section
        if (storyId != null) {
            filtered = applyStoryIdFilter(filtered, storyId);
        }

        return filtered;
    }

    /**
     * Defensive non-broadening guard. Primary commit-window filtering is
     * performed by {@code RepositoryContextAdapter.filterByStoryScope} using
     * authoritative repository history (ProjectCommitRepository BFS traversal).
     * This guard ensures no TECHNICAL_EVIDENCE without any commit attribution
     * leaked through.
     */
    private List<EngineeringEvidence> applyStoryIdFilter(
            List<EngineeringEvidence> evidence,
            UUID storyId
    ) {
        if (storyId == null) return evidence;
        return evidence.stream()
                .filter(e -> {
                    if (e.trustTier() != TrustTier.TECHNICAL_EVIDENCE) return true;
                    return e.identifier() != null
                            || (e.relatedReferences() != null && !e.relatedReferences().isEmpty());
                })
                .collect(Collectors.toList());
    }

    private List<ContextSection> buildSections(Map<TrustTier, List<EngineeringEvidence>> evidenceByTier) {
        List<ContextSection> sections = new ArrayList<>();

        // Section order: TRUSTED, HUMAN_AUTHORED, TECHNICAL_EVIDENCE, SYSTEM_METADATA
        sections.add(buildSection(
                "trusted_knowledge",
                TrustTier.TRUSTED,
                evidenceByTier.getOrDefault(TrustTier.TRUSTED, List.of()),
                "Validated project knowledge governing this task"
        ));

        sections.add(buildSection(
                "human_context",
                TrustTier.HUMAN_AUTHORED,
                evidenceByTier.getOrDefault(TrustTier.HUMAN_AUTHORED, List.of()),
                "Human-authored project context and constraints"
        ));

        sections.add(buildSection(
                "technical_evidence",
                TrustTier.TECHNICAL_EVIDENCE,
                evidenceByTier.getOrDefault(TrustTier.TECHNICAL_EVIDENCE, List.of()),
                "Repository-derived technical evidence"
        ));

        sections.add(buildSection(
                "system_metadata",
                TrustTier.SYSTEM_METADATA,
                evidenceByTier.getOrDefault(TrustTier.SYSTEM_METADATA, List.of()),
                "System metadata for context interpretation"
        ));

        return sections;
    }

    private ContextSection buildSection(
            String name,
            TrustTier trustTier,
            List<EngineeringEvidence> evidence,
            String rationale
    ) {
        List<EngineeringEvidence> ordered = orderEvidence(evidence);
        return new ContextSection(name, trustTier, ordered, rationale);
    }

    private List<EngineeringEvidence> orderEvidence(List<EngineeringEvidence> evidence) {
        return evidence.stream()
                .sorted(Comparator
                        .<EngineeringEvidence, Boolean>comparing(e -> e.occurredAt() == null)
                        .thenComparing(
                                Comparator.comparing(EngineeringEvidence::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        )
                        .thenComparing(EngineeringEvidence::identifier, Comparator.nullsLast(Comparator.naturalOrder()))
                )
                .toList();
    }

    private TrustTier classifyTrustTier(RepositoryEvidence evidence) {
        String kind = evidence.kind();
        String sourceType = evidence.provenance() != null ? evidence.provenance().sourceType() : null;

        // TRUSTED: validated knowledge from accepted proposals
        if ("CORE_KNOWLEDGE".equals(sourceType)
                && (kind.equals("INSIGHT") || kind.equals("DECISION") || kind.equals("ENGINEERING_EVENT"))) {
            return TrustTier.TRUSTED;
        }

        // HUMAN_AUTHORED: human context inputs, repository documents
        if (kind.equals("PROJECT_NOTE")
                || kind.equals("MILESTONE")
                || kind.equals("ARTIFACT")
                || kind.equals("ENGINEERING_STORY")
                || kind.equals("CHALLENGE")) {
            return TrustTier.HUMAN_AUTHORED;
        }

        // SYSTEM_METADATA: analysis execution metadata, freshness, diagnostics
        if (kind.equals("ANALYSIS")
                || kind.equals("FRESHNESS")
                || kind.equals("DIAGNOSTIC")
                || kind.equals("SELECTION_METADATA")) {
            return TrustTier.SYSTEM_METADATA;
        }

        // TECHNICAL_EVIDENCE: repository-derived
        if (sourceType != null
                && (sourceType.equals("GIT")
                || sourceType.equals("DETERMINISTIC_EXTRACTION")
                || sourceType.equals("CORE_ANALYSIS")
                || sourceType.equals("REPOSITORY_STRUCTURE"))) {
            return TrustTier.TECHNICAL_EVIDENCE;
        }

        // EXCLUDED: unvalidated proposals, AI engine output
        if (kind.equals("VALIDATABLE_PROPOSAL") || "AI_ENGINE".equals(sourceType)) {
            return null;
        }

        // Unknown/unsupported kinds are excluded (not silently classified)
        return null;
    }

    private EngineeringEvidence mapEvidence(
            RepositoryEvidence evidence,
            RepositoryContext repositoryContext,
            String projectSlug
    ) {
        var provenance = evidence.provenance();

        TrustTier trustTier = classifyTrustTier(evidence);

        // Filter out EXCLUDED evidence (UNVALIDATED/TRANSIENT_AI)
        if (trustTier == null) {
            return null;
        }

        return new EngineeringEvidence(
                evidence.kind(),
                evidence.layer().name(),
                evidence.summary(),
                provenance.sourceType(),
                provenance.originatingFile(),
                provenance.identifier(),
                evidence.relevanceScore(),
                selectionReason(evidence, repositoryContext),
                evidence.occurredAt(),
                evidence.relatedReferences(),
                evidence.extractionMetadata(),
                mapContent(evidence.content()),
                mapSymbols(evidence.symbols()),
                resolveResource(evidence, projectSlug),
                trustTier
        );
    }

    private List<EngineeringEvidence> mapEvidence(
            RepositoryContext repositoryContext,
            String projectSlug
    ) {
        return repositoryContext.evidence().stream()
                .map(evidence -> mapEvidence(evidence, repositoryContext, projectSlug))
                .filter(e -> e != null) // Filter out EXCLUDED
                .toList();
    }

    /**
     * Deterministic, exact-only mapping between an evidence and the MCP
     * resource exposing the same artifact (Stories 0088/0089). Anything that
     * is not an unambiguous correspondence stays null: absence of a resource
     * is a normal state, never a failure.
     */
    private String resolveResource(RepositoryEvidence evidence, String projectSlug) {
        String identifier = evidence.provenance() == null
                ? null : evidence.provenance().identifier();
        try {
            return switch (evidence.kind()) {
                case "DECISION" -> DevlogResourceUriFactory.decision(
                        projectSlug, UUID.fromString(identifier));
                case "INSIGHT" -> DevlogResourceUriFactory.insight(
                        projectSlug, UUID.fromString(identifier));
                case "ENGINEERING_STORY" -> DevlogResourceUriFactory.story(
                        projectSlug, UUID.fromString(identifier));
                case "ENGINEERING_EVENT" -> DevlogResourceUriFactory.engineeringEvent(
                        projectSlug, UUID.fromString(identifier));
                case "COMMIT" -> commitResource(evidence.reference(), projectSlug);
                default -> null;
            };
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * A COMMIT evidence IS the commit itself; its internal reference carries
     * the SHA exactly once: {@code git:{sourceId}:{sha}}.
     */
    private String commitResource(String reference, String projectSlug) {
        var matcher = GIT_COMMIT_REFERENCE.matcher(
                reference == null ? "" : reference);
        if (!matcher.matches()) return null;
        return DevlogResourceUriFactory.commit(projectSlug, matcher.group(1));
    }

    private EngineeringEvidenceContent mapContent(
            com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent content
    ) {
        if (content == null) return null;
        return new EngineeringEvidenceContent(
                content.status().name(),
                content.text(),
                content.reason(),
                content.revision()
        );
    }

    private EngineeringEvidenceSymbols mapSymbols(
            com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols symbols
    ) {
        if (symbols == null) return null;
        List<EngineeringSymbolDeclaration> declarations = symbols.declarations()
                .stream()
                .map(declaration -> new EngineeringSymbolDeclaration(
                        declaration.kind().name(),
                        declaration.name(),
                        declaration.owningType(),
                        declaration.modifiers(),
                        declaration.returnType(),
                        declaration.parameters().stream()
                                .map(parameter -> new EngineeringSymbolParameter(
                                        parameter.type(), parameter.name()))
                                .toList(),
                        declaration.annotations(),
                        declaration.location() == null ? null
                                : new EngineeringSymbolLocation(
                                declaration.location().beginLine(),
                                declaration.location().beginColumn(),
                                declaration.location().endLine(),
                                declaration.location().endColumn())
                ))
                .toList();
        return new EngineeringEvidenceSymbols(
                symbols.status().name(),
                symbols.truncated(),
                symbols.returnedSymbolCount(),
                symbols.availableSymbolCount(),
                symbols.extractorId(),
                symbols.extractorVersion(),
                symbols.revision(),
                declarations
        );
    }

    private EngineeringContextMetadata mapMetadata(
            RepositoryContext context,
            ProjectFreshnessSummary freshnessSummary
    ) {
        List<String> warnings = new ArrayList<>(context.warnings());
        EngineeringContextFreshness freshness =
                buildFreshness(context, freshnessSummary, warnings);
        return new EngineeringContextMetadata(
                context.candidateCount(),
                context.evidence().size(),
                context.truncated(),
                context.usedTokens(),
                context.contextDigest(),
                warnings,
                freshness
        );
    }

    /**
     * Declares what revision this response represents (ADR-062). Persisted
     * freshness rows describe the last recorded observation per source; the
     * revisions resolved while building THIS response are the freshest
     * observation available and take precedence for a single-source project.
     * The aggregate never fabricates a single revision for multi-source
     * projects — the per-source breakdown carries the truth.
     */
    private EngineeringContextFreshness buildFreshness(
            RepositoryContext context,
            ProjectFreshnessSummary summary,
            List<String> warnings
    ) {
        List<EngineeringContextFreshness.SourceFreshness> sources = new ArrayList<>();
        if (summary != null) {
            for (ProjectFreshnessResponse row : summary.checkedSources()) {
                sources.add(new EngineeringContextFreshness.SourceFreshness(
                        row.source().id(),
                        row.source().name(),
                        row.status().name(),
                        row.guidance().name(),
                        row.source().currentRevision(),
                        row.baseline() == null ? null : row.baseline().analyzedRevision(),
                        row.checkedAt()
                ));
            }
        }
        Set<String> liveRevisions = liveRevisions(context.evidence());

        if (liveRevisions.size() == 1 && sources.size() == 1) {
            String observed = liveRevisions.iterator().next();
            EngineeringContextFreshness.SourceFreshness only = sources.get(0);
            if (only.contextRevision() != null
                    && !observed.equalsIgnoreCase(only.contextRevision())) {
                sources.set(0, new EngineeringContextFreshness.SourceFreshness(
                        only.sourceId(), only.name(),
                        EngineeringContextFreshness.STATUS_STALE,
                        "REFRESH_RECOMMENDED",
                        observed,
                        only.contextRevision(),
                        only.checkedAt()));
            } else if (only.observedRevision() == null
                    || !observed.equalsIgnoreCase(only.observedRevision())) {
                sources.set(0, new EngineeringContextFreshness.SourceFreshness(
                        only.sourceId(), only.name(), only.status(), only.guidance(),
                        observed, only.contextRevision(), only.checkedAt()));
            }
        }
        String repositoryRevision;
        if (liveRevisions.size() == 1) {
            repositoryRevision = liveRevisions.iterator().next();
        } else if (liveRevisions.isEmpty() && sources.size() == 1) {
            repositoryRevision = sources.get(0).observedRevision();
        } else {
            repositoryRevision = null;
        }
        String contextRevision = sources.size() == 1 ? sources.get(0).contextRevision() : null;

        String aggregate = aggregateStatus(sources);
        if (aggregate == null && repositoryRevision != null) {
            // no recorded knowledge state at all, but the repository was
            // observed during this build: never present this as clean
            aggregate = EngineeringContextFreshness.STATUS_NO_BASELINE;
        }
        boolean anyStale = EngineeringContextFreshness.STATUS_STALE.equals(aggregate)
                || sources.stream().anyMatch(source ->
                        EngineeringContextFreshness.STATUS_STALE.equals(source.status()))
                || (EngineeringContextFreshness.STATUS_NO_BASELINE.equals(aggregate)
                        && repositoryRevision != null);
        if (anyStale) {
            warnings.add(WARNING_PROJECT_CONTEXT_STALE);
        }
        if (EngineeringContextFreshness.STATUS_PARTIALLY_FRESH.equals(aggregate)) {
            warnings.add(WARNING_PROJECT_CONTEXT_PARTIALLY_FRESH);
        }

        return new EngineeringContextFreshness(
                aggregate, repositoryRevision, contextRevision,
                List.copyOf(sources));
    }

    private String aggregateStatus(
            List<EngineeringContextFreshness.SourceFreshness> sources) {
        Set<String> statuses = new LinkedHashSet<>();
        sources.forEach(source -> statuses.add(source.status()));
        if (statuses.isEmpty()) return null;
        if (statuses.size() == 1) return statuses.iterator().next();
        if (statuses.contains(EngineeringContextFreshness.STATUS_CURRENT)
                && statuses.contains(EngineeringContextFreshness.STATUS_STALE)) {
            return EngineeringContextFreshness.STATUS_PARTIALLY_FRESH;
        }
        for (String candidate : new String[]{EngineeringContextFreshness.STATUS_STALE,
                EngineeringContextFreshness.STATUS_NO_BASELINE,
                EngineeringContextFreshness.STATUS_UNKNOWN}) {
            if (statuses.contains(candidate)) return candidate;
        }
        return EngineeringContextFreshness.STATUS_CURRENT;
    }

    private Set<String> liveRevisions(List<RepositoryEvidence> evidence) {
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (RepositoryEvidence item : evidence) {
            addRevision(result, item.extractionMetadata() == null
                    ? null : item.extractionMetadata().get("resolvedRevision"));
            addRevision(result, item.content() == null ? null : item.content().revision());
            addRevision(result, item.symbols() == null ? null : item.symbols().revision());
        }
        return result;
    }

    private void addRevision(Set<String> target, String revision) {
        if (revision != null && !revision.isBlank()) target.add(revision);
    }

    private String selectionReason(
            RepositoryEvidence evidence,
            RepositoryContext context
    ) {
        return context.selectionDecisions().stream()
                .filter(decision ->
                        decision.evidenceReference().equals(evidence.reference())
                )
                .map(RepositoryContext.SelectionDecision::reason)
                .findFirst()
                .orElse(null);
    }
}