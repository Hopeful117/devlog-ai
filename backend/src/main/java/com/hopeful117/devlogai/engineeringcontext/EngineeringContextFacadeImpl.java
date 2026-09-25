package com.hopeful117.devlogai.engineeringcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.mapper.EngineeringContextContractMapper;
import com.hopeful117.devlogai.project.service.ProjectService;
import com.hopeful117.devlogai.projectcontext.ProjectContextProvider;
import com.hopeful117.devlogai.projectcontext.RepositoryContextAdapter;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;

@Service
@RequiredArgsConstructor
public class EngineeringContextFacadeImpl implements EngineeringContextFacade {
    private final ProjectService projectService;
    private final ProjectContextProvider projectContextProvider;
    private final RepositoryContextAdapter repositoryContextAdapter;
    private final ProjectFreshnessService freshnessService;
    private final EngineeringContextContractMapper mapper;

    @Override
    public EngineeringContext getEngineeringContext(
            String projectSlug,
            String intent,
            List<String> files,
            UUID storyId
    ) {
        var project = projectService.getBySlug(projectSlug);
        var projectId = project.getId();

        var projectContext =
                projectContextProvider.build(projectId);

        var repositoryContext =
                repositoryContextAdapter.buildRepositoryContext(
                        projectId,
                        intent,
                        projectContext,
                        files,
                        storyId
                );

        return mapper.toContract(
                projectContext,
                repositoryContext,
                intent,
                files,
                storyId,
                freshnessService.summary(projectId)
        );
    }

    @Override
    public CanonicalEngineeringContext getCanonicalEngineeringContext(
            String projectSlug, String intent, List<String> files, UUID storyId) {
        var project = projectService.getBySlug(projectSlug);
        var projectContext = projectContextProvider.build(project.getId());
        var repositoryContext = repositoryContextAdapter.buildRepositoryContext(
                project.getId(), intent, projectContext, files, storyId);
        var freshness = freshnessService.summary(project.getId());
        EngineeringContext context = mapper.toContract(
                projectContext, repositoryContext, intent, files, storyId, freshness);
        var references = repositoryContext.evidence().stream()
                .filter(e -> e.reference() != null && !e.reference().isBlank())
                .map(e -> new EvidenceRef(e.reference(), e.provenance() == null
                        ? e.reference() : e.provenance().repositoryLocation()))
                .toList();
        Map<String, String> trust = references.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                EvidenceRef::reference, ref -> "TECHNICAL_EVIDENCE", (left, right) -> left));
        Map<String, List<String>> relations = repositoryContext.evidence().stream()
                .filter(e -> e.reference() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        RepositoryEvidence::reference,
                        e -> e.relatedReferences() == null ? List.of() : List.copyOf(e.relatedReferences()),
                        (left, right) -> left));
        Map<String, RepositoryEvidence.EvidenceProvenance> provenance = repositoryContext.evidence().stream()
                .filter(e -> e.reference() != null && e.provenance() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        RepositoryEvidence::reference, RepositoryEvidence::provenance, (left, right) -> left));
        Map<String, Object> accounting = Map.of(
                "candidateCount", repositoryContext.candidateCount(),
                "selectedCount", repositoryContext.evidence().size(),
                "discardedCount", repositoryContext.discardedCount(),
                "usedTokens", repositoryContext.usedTokens(),
                "truncated", repositoryContext.truncated());
        return new CanonicalEngineeringContext(context, repositoryContext,
                repositoryContext.contextDigest(), repositoryContext.contextVersion(),
                context.requestEcho(), Map.of("summary", freshness), accounting, repositoryContext.diagnostics(),
                relations, provenance, trust, references);
    }
}