package com.hopeful117.devlogai.history.context;

import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.model.FileChangeType;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.source.entity.Source;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitDiffContextBuilderTest {
    @Test
    void buildsBoundedTraceableContextAndDetectsKnowledgeReferences() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        ProjectCommit commit = ProjectCommit.builder()
                .project(Project.builder().id(projectId).build())
                .source(Source.builder().id(sourceId).build())
                .commitHash("abc123").committedAt(Instant.parse("2026-07-23T10:00:00Z"))
                .fullMessage("implement history").subject("implement history")
                .rootCommit(false).mergeCommit(true).filesChanged(4)
                .insertions(60).deletions(1).binaryFiles(1).importedAt(Instant.now()).build();
        commit.addParent(0, "parent-one");
        commit.addParent(1, "parent-two");
        commit.addChangedFile(file("docs/decisions/ADR-036.md", false, 10));
        commit.addChangedFile(file("docs/roadmap.md", false, 10));
        commit.addChangedFile(file("vendor/library.js", false, 10));
        commit.addChangedFile(file("asset.bin", true, 0));

        CommitDiffAnalysisContext context = new CommitDiffContextBuilder(3, 50).build(commit);

        assertTrue(context.truncated());
        assertEquals("parent-one", context.firstParentHash());
        assertEquals(3, context.changedFiles().size());
        assertEquals(4, context.evidenceReferences().size(),
                "truncation must not remove evidence references");
        assertEquals(java.util.List.of("docs/decisions/ADR-036.md"),
                context.candidateAdrReferences());
        assertEquals(java.util.List.of("docs/roadmap.md"),
                context.candidateRoadmapReferences());
        assertTrue(context.warnings().contains("CHANGED_FILES_TRUNCATED"));
        assertTrue(context.warnings().contains("DIFF_SIZE_LIMIT_EXCEEDED"));
        assertTrue(context.warnings().contains("BINARY_FILES_EXCLUDED"));
        assertTrue(context.warnings().contains("MERGE_COMMIT_FIRST_PARENT_DIFF"));
        assertTrue(context.changedFiles().get(2).excludedFromAnalysis());
        assertEquals("GENERATED_OR_VENDOR_PATH",
                context.changedFiles().get(2).exclusionReason());
    }

    private ChangedFile file(String path, boolean binary, int insertions) {
        return ChangedFile.builder().changeType(FileChangeType.MODIFIED)
                .oldPath(path).newPath(path).binary(binary)
                .insertions(insertions).deletions(0).build();
    }
}
