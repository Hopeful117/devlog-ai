package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.history.model.FileChangeType;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DiffEvidenceResolverTest {
    private final ProjectCommitRepository commits = mock(ProjectCommitRepository.class);
    private final SourceRepository sources = mock(SourceRepository.class);
    private final DiffEvidenceResolver resolver = new DiffEvidenceResolver(commits, sources);
    private final UUID sourceId = UUID.randomUUID();
    private final ParsedEvidenceReference reference = new ParsedEvidenceReference(
            "diff:" + sourceId + ":abcdef1:src/App.java", EvidenceResolutionFamily.DIFF,
            sourceId, "abcdef1", "src/App.java");

    @Test void rejectsNullChangedFile() {
        assertMalformed(ProjectCommit.builder().changedFiles(java.util.Arrays.asList((ChangedFile) null)).parents(List.of()).build());
    }

    @Test void rejectsChangedFileWithoutChangeType() {
        assertMalformed(ProjectCommit.builder().changedFiles(List.of(ChangedFile.builder().newPath("src/App.java").build()))
                .parents(List.of()).build());
    }

    @Test void rejectsNullParent() {
        assertMalformed(ProjectCommit.builder().changedFiles(List.of()).parents(java.util.Arrays.asList((com.hopeful117.devlogai.history.entity.CommitParent) null)).build());
    }

    @Test void rejectsBlankCommitMetadata() {
        ProjectCommit commit = validCommit();
        commit.setCommitHash(" ");
        assertMalformed(commit);
        commit = validCommit();
        commit.setSubject(" ");
        assertMalformed(commit);
        commit = validCommit();
        commit.setFullMessage(" ");
        assertMalformed(commit);
    }

    @Test void rejectsBlankParentHash() {
        var commit = validCommit();
        commit.setParents(List.of(com.hopeful117.devlogai.history.entity.CommitParent.builder()
                .parentHash(" ").build()));
        assertMalformed(commit);
    }

    private ProjectCommit validCommit() {
        return ProjectCommit.builder().commitHash("abcdef1").subject("subject").fullMessage("message")
                .committedAt(Instant.EPOCH).changedFiles(List.of()).parents(List.of()).build();
    }

    private void assertMalformed(ProjectCommit commit) {
        if (commit.getCommitHash() == null) commit.setCommitHash("abcdef1");
        if (commit.getSubject() == null) commit.setSubject("subject");
        if (commit.getFullMessage() == null) commit.setFullMessage("message");
        if (commit.getCommittedAt() == null) commit.setCommittedAt(Instant.EPOCH);
        when(sources.findById(sourceId)).thenReturn(Optional.of(mock(Source.class)));
        when(commits.findBySourceIdAndCommitHash(sourceId, "abcdef1")).thenReturn(Optional.of(commit));
        when(commits.findWithChangedFilesBySourceIdAndCommitHash(sourceId, "abcdef1"))
                .thenReturn(Optional.of(commit));
        assertThrows(EvidenceResolutionException.class,
                () -> resolver.resolve(reference, new EvidenceResolutionRequest(reference.canonicalReference(), EvidenceResolutionMode.CURRENT)));
    }
}
