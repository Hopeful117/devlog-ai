package com.hopeful117.devlogai.engineeringcontext.mapper;

import com.hopeful117.devlogai.contracts.engineeringcontext.DevlogResourceUriFactory;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextMetadata;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidence;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidenceContent;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidenceSymbols;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringSymbolDeclaration;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringSymbolLocation;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringSymbolParameter;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.mapper.ProjectContextContractMapper;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class EngineeringContextContractMapper {

    private static final Pattern GIT_COMMIT_REFERENCE =
            Pattern.compile("^git:[0-9a-fA-F\\-]+:([0-9a-fA-F]{40}|[0-9a-fA-F]{64})$");

    private final ProjectContextContractMapper projectContextContractMapper;

    public EngineeringContext toContract(
            ProjectContextSnapshot projectContext,
            RepositoryContext repositoryContext,
            String intent
    ) {
        String projectSlug = projectContext.project().slug();
        return new EngineeringContext(
                projectContextContractMapper.toContract(projectContext),
                intent,
                mapEvidence(repositoryContext, projectSlug),
                mapMetadata(repositoryContext)
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
            RepositoryContext context
    ) {
        return new EngineeringContextMetadata(
                context.candidateCount(),
                context.evidence().size(),
                context.truncated(),
                context.usedTokens(),
                context.contextDigest(),
                context.warnings()
        );
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