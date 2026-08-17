package com.hopeful117.devlogai.engineeringcontext.mapper;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextMetadata;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidence;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.mapper.ProjectContextContractMapper;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EngineeringContextContractMapper {

    private final ProjectContextContractMapper projectContextContractMapper;

    public EngineeringContext toContract(
            ProjectContextSnapshot projectContext,
            RepositoryContext repositoryContext,
            String intent
    ) {
        return new EngineeringContext(
                projectContextContractMapper.toContract(projectContext),
                intent,
                mapEvidence(repositoryContext),
                mapMetadata(repositoryContext)
        );
    }

    private List<EngineeringEvidence> mapEvidence(
            RepositoryContext repositoryContext
    ) {
        return repositoryContext.evidence().stream()
                .map(evidence -> mapEvidence(evidence, repositoryContext))
                .toList();
    }

    private EngineeringEvidence mapEvidence(
            RepositoryEvidence evidence,
            RepositoryContext repositoryContext
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
                selectionReason(evidence, repositoryContext)
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
                context.contextDigest()
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