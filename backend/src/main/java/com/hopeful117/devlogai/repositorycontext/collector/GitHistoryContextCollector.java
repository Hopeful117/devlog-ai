package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(30)
public class GitHistoryContextCollector implements RepositoryContextCollector {
    private final ProjectCommitRepository commitRepository;
    private final EvidenceFactory evidenceFactory;

    public GitHistoryContextCollector(
            ProjectCommitRepository commitRepository,
            EvidenceFactory evidenceFactory
    ) {
        this.commitRepository = commitRepository;
        this.evidenceFactory = evidenceFactory;
    }

    @Override public String collectorId() { return "git-history"; }
    @Override public String collectorVersion() { return "v1"; }

    @Override
    public List<RepositoryEvidence> collect(ContextRequest request) {
        if (request.budget().maximumHistoryItems() == 0) return List.of();
        List<ProjectCommit> commits =
                commitRepository.findByProjectIdOrderByCommittedAtDescCommitHashDesc(
                        request.analysisContext().project().id(),
                        PageRequest.of(0, request.budget().maximumHistoryItems()));
        List<RepositoryEvidence> evidence = new ArrayList<>();
        for (ProjectCommit commit : commits) {
            String repositoryId = commit.getSource().getId().toString();
            String reference = "git:" + repositoryId + ":" + commit.getCommitHash();
            evidence.add(evidenceFactory.create(metadata(),
                    RepositoryContextLayer.GIT_HISTORY, "COMMIT", reference,
                    commit.getSubject() + " — " + commit.getFilesChanged()
                            + " files, +" + commit.getInsertions()
                            + "/-" + commit.getDeletions(),
                    commit.getCommittedAt(), 700,
                    commit.getParents().stream().map(parent ->
                            "git:" + repositoryId + ":" + parent.getParentHash()).toList(),
                    repositoryId, null, commit.getCommitHash(),
                    request.budget().maximumSummaryCharacters()));
        }
        return List.copyOf(evidence);
    }

    private EvidenceFactory.ContextRequestMetadata metadata() {
        return new EvidenceFactory.ContextRequestMetadata(
                collectorId(), collectorVersion(), "GIT");
    }
}
