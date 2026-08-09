package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.projectcontext.projection.AgentContextProjectionService;
import com.hopeful117.devlogai.projectcontext.projection.AgentEngineeringStoryContext;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EngineeringStoryContextServiceImpl implements EngineeringStoryContextService {

    private final ProjectContextProvider projectContextProvider;
    private final RepositoryContextAdapter repositoryContextAdapter;
    private final AgentContextProjectionService projectionService;
    private final ProjectFreshnessService freshnessService;

    @Override
    public EngineeringStoryContext build(UUID projectId) {
        ProjectContextSnapshot snapshot = projectContextProvider.build(projectId);
        return new EngineeringStoryContext(snapshot, Instant.now(), projectId, null,
                freshnessService.summary(projectId));
    }

    @Override
    public EngineeringStoryContext buildWithRepositoryContext(
            UUID projectId, String storyDescription) {
        long started = System.nanoTime();
        long snapshotStarted = System.nanoTime();
        ProjectContextSnapshot snapshot = projectContextProvider.build(projectId);
        long snapshotMillis = elapsedMillis(snapshotStarted);
        long contextStarted = System.nanoTime();
        RepositoryContext repositoryContext =
                repositoryContextAdapter.buildRepositoryContext(
                        projectId, storyDescription, snapshot);
        EngineeringStoryContext result = new EngineeringStoryContext(
                snapshot, Instant.now(), projectId, repositoryContext,
                freshnessService.summary(projectId));
        log.info("Engineering Story Context completed projectId={} mode=full snapshotMs={} contextMs={} candidates={} selected={} contextDigest={} totalMs={}",
                projectId, snapshotMillis, elapsedMillis(contextStarted),
                repositoryContext.candidateCount(), repositoryContext.evidence().size(),
                repositoryContext.contextDigest(), elapsedMillis(started));
        return result;
    }

    @Override
    public AgentEngineeringStoryContext buildAgentWithRepositoryContext(
            UUID projectId, String storyDescription) {
        long started = System.nanoTime();
        long snapshotStarted = System.nanoTime();
        ProjectContextSnapshot snapshot = projectContextProvider.build(projectId);
        long snapshotMillis = elapsedMillis(snapshotStarted);
        long contextStarted = System.nanoTime();
        RepositoryContext repositoryContext = repositoryContextAdapter
                .buildRepositoryContext(projectId, storyDescription, snapshot);
        long contextMillis = elapsedMillis(contextStarted);
        long projectionStarted = System.nanoTime();
        AgentEngineeringStoryContext result = projectionService.project(
                projectId, snapshot, repositoryContext, Instant.now(),
                freshnessService.summary(projectId));
        var projected = result.repositoryContext();
        log.info("Engineering Story Context completed projectId={} mode=agent snapshotMs={} contextMs={} projectionMs={} candidates={} selected={} canonicalBytes={} contextDigest={} projectionDigest={} totalMs={}",
                projectId, snapshotMillis, contextMillis,
                elapsedMillis(projectionStarted), repositoryContext.candidateCount(),
                projected.evidence().size(), projected.accounting().canonicalBytes(),
                repositoryContext.contextDigest(), projected.projectionDigest(),
                elapsedMillis(started));
        return result;
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
