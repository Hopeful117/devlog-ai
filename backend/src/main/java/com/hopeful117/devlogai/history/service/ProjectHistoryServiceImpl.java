package com.hopeful117.devlogai.history.service;

import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.history.context.CommitDiffAnalysisContext;
import com.hopeful117.devlogai.history.context.CommitDiffContextBuilder;
import com.hopeful117.devlogai.history.dto.HistoryImportResult;
import com.hopeful117.devlogai.history.dto.ProjectCommitResponse;
import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.model.GitCommitData;
import com.hopeful117.devlogai.history.provider.GitHistoryProvider;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectHistoryServiceImpl implements ProjectHistoryService {
    private final SourceRepository sourceRepository;
    private final ProjectCommitRepository commitRepository;
    private final WorkspaceManager workspaceManager;
    private final GitHistoryProvider historyProvider;
    private final CommitDiffContextBuilder contextBuilder;

    public ProjectHistoryServiceImpl(
            SourceRepository sourceRepository,
            ProjectCommitRepository commitRepository,
            WorkspaceManager workspaceManager,
            GitHistoryProvider historyProvider,
            CommitDiffContextBuilder contextBuilder
    ) {
        this.sourceRepository = sourceRepository;
        this.commitRepository = commitRepository;
        this.workspaceManager = workspaceManager;
        this.historyProvider = historyProvider;
        this.contextBuilder = contextBuilder;
    }

    @Override
    public HistoryImportResult importHistory(UUID repositoryId, String targetRevision) {
        Source source = sourceRepository.findById(repositoryId)
                .orElseThrow(() -> new EntityNotFoundException("Source", repositoryId));
        SynchronizedWorkspace workspace = workspaceManager.synchronize(source, targetRevision);
        List<GitCommitData> discovered =
                historyProvider.readHistory(workspace.path(), workspace.resolvedRevision());
        int imported = 0;
        for (GitCommitData data : discovered) {
            if (commitRepository.existsBySourceIdAndCommitHash(repositoryId, data.commitHash()))
                continue;
            commitRepository.save(toEntity(source, data, Instant.now()));
            imported++;
        }
        return new HistoryImportResult(repositoryId, workspace.resolvedRevision(), discovered.size(),
                imported, discovered.size() - imported);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectCommitResponse> getProjectHistory(UUID projectId) {
        return commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CommitDiffAnalysisContext getCommitContext(UUID repositoryId, String commitHash) {
        ProjectCommit commit = commitRepository.findBySourceIdAndCommitHash(repositoryId, commitHash)
                .orElseThrow(() -> new EntityNotFoundException("ProjectCommit", commitHash));
        return contextBuilder.build(commit);
    }

    private ProjectCommit toEntity(Source source, GitCommitData data, Instant importedAt) {
        ProjectCommit commit = ProjectCommit.builder()
                .project(source.getProject()).source(source).commitHash(data.commitHash())
                .authorName(data.authorName()).authorEmail(data.authorEmail())
                .authoredAt(data.authoredAt()).committedAt(data.committedAt())
                .subject(data.subject()).fullMessage(data.fullMessage())
                .rootCommit(data.rootCommit()).mergeCommit(data.mergeCommit())
                .filesChanged(data.statistics().filesChanged())
                .insertions(data.statistics().insertions()).deletions(data.statistics().deletions())
                .binaryFiles(data.statistics().binaryFiles()).importedAt(importedAt).build();
        for (int index = 0; index < data.parentHashes().size(); index++)
            commit.addParent(index, data.parentHashes().get(index));
        data.changedFiles().forEach(file -> commit.addChangedFile(ChangedFile.builder()
                .changeType(file.changeType()).oldPath(file.oldPath()).newPath(file.newPath())
                .binary(file.binary()).insertions(file.insertions()).deletions(file.deletions())
                .build()));
        return commit;
    }

    private ProjectCommitResponse toResponse(ProjectCommit commit) {
        return new ProjectCommitResponse(commit.getId(), commit.getProject().getId(),
                commit.getSource().getId(), commit.getCommitHash(),
                commit.getParents().stream().map(parent -> parent.getParentHash()).toList(),
                commit.getAuthorName(), commit.getAuthorEmail(), commit.getAuthoredAt(),
                commit.getCommittedAt(), commit.getSubject(), commit.getFullMessage(),
                commit.isRootCommit(), commit.isMergeCommit(), commit.getFilesChanged(),
                commit.getInsertions(), commit.getDeletions(), commit.getBinaryFiles(),
                commit.getImportedAt(), commit.getChangedFiles().stream()
                .map(file -> new ProjectCommitResponse.ChangedFileResponse(
                        file.getChangeType(), file.getOldPath(), file.getNewPath(), file.isBinary(),
                        file.getInsertions(), file.getDeletions())).toList());
    }
}
