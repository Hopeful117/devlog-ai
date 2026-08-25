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
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.mapper.ProjectContextContractMapper;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

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
            ProjectFreshnessSummary freshnessSummary
    ) {
        String projectSlug = projectContext.project().slug();
        return new EngineeringContext(
                projectContextContractMapper.toContract(projectContext),
                intent,
                mapEvidence(repositoryContext, projectSlug),
                mapMetadata(repositoryContext, freshnessSummary)
        );
    }

    private List<EngineeringEvidence> mapEvidence(
            RepositoryContext repositoryContext,
            String projectSlug
    ) {
        return repositoryContext.evidence().stream()
                .map(evidence -> mapEvidence(evidence, repositoryContext, projectSlug))
                .toList();
    }

    private EngineeringEvidence mapEvidence(
            RepositoryEvidence evidence,
            RepositoryContext repositoryContext,
            String projectSlug
    ) {
        var provenance = evidence.provenance();

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
                resolveResource(evidence, projectSlug)
        );
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