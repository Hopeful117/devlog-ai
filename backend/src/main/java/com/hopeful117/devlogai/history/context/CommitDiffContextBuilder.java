package com.hopeful117.devlogai.history.context;

import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.model.DiffStatistics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class CommitDiffContextBuilder {
    private static final Set<String> GENERATED_SEGMENTS = Set.of(
            "node_modules", "vendor", "target", "build", "dist", "coverage", ".venv", "venv"
    );
    private final int maximumFiles;
    private final int maximumChangedLines;

    public CommitDiffContextBuilder(
            @Value("${devlog.history.context.max-files:200}") int maximumFiles,
            @Value("${devlog.history.context.max-changed-lines:20000}") int maximumChangedLines
    ) {
        if (maximumFiles < 1 || maximumChangedLines < 1)
            throw new IllegalArgumentException("History context limits must be positive");
        this.maximumFiles = maximumFiles;
        this.maximumChangedLines = maximumChangedLines;
    }

    public CommitDiffAnalysisContext build(ProjectCommit commit) {
        List<String> warnings = new ArrayList<>();
        int changedLines = commit.getInsertions() + commit.getDeletions();
        boolean truncated = commit.getChangedFiles().size() > maximumFiles
                || changedLines > maximumChangedLines;
        if (commit.getChangedFiles().size() > maximumFiles)
            warnings.add("CHANGED_FILES_TRUNCATED");
        if (changedLines > maximumChangedLines)
            warnings.add("DIFF_SIZE_LIMIT_EXCEEDED");
        if (commit.getBinaryFiles() > 0)
            warnings.add("BINARY_FILES_EXCLUDED");
        if (commit.isRootCommit()) warnings.add("ROOT_COMMIT_NO_PARENT");
        if (commit.isMergeCommit()) warnings.add("MERGE_COMMIT_FIRST_PARENT_DIFF");

        List<CommitDiffAnalysisContext.ChangedFileContext> files =
                commit.getChangedFiles().stream().limit(maximumFiles)
                        .map(file -> toContext(commit, file)).toList();
        List<String> evidence = commit.getChangedFiles().stream()
                .map(file -> evidence(commit, path(file))).toList();
        List<String> adrs = commit.getChangedFiles().stream().map(this::path)
                .filter(this::isAdr).distinct().toList();
        List<String> roadmap = commit.getChangedFiles().stream().map(this::path)
                .filter(this::isRoadmap).distinct().toList();
        List<String> parents = commit.getParents().stream()
                .map(parent -> parent.getParentHash()).toList();
        return new CommitDiffAnalysisContext(
                commit.getProject().getId(), commit.getSource().getId(), commit.getCommitHash(),
                parents.isEmpty() ? null : parents.getFirst(), parents,
                commit.isRootCommit(), commit.isMergeCommit(), commit.getFullMessage(),
                commit.getCommittedAt(), files,
                new DiffStatistics(commit.getFilesChanged(), commit.getInsertions(),
                        commit.getDeletions(), commit.getBinaryFiles()),
                adrs, roadmap, evidence, truncated, List.copyOf(warnings)
        );
    }

    private CommitDiffAnalysisContext.ChangedFileContext toContext(
            ProjectCommit commit, ChangedFile file
    ) {
        String path = path(file);
        String exclusion = exclusion(path, file.isBinary());
        return new CommitDiffAnalysisContext.ChangedFileContext(
                file.getChangeType(), file.getOldPath(), file.getNewPath(), file.isBinary(),
                file.getInsertions(), file.getDeletions(), language(path), category(path),
                exclusion != null, exclusion, evidence(commit, path)
        );
    }

    private String exclusion(String path, boolean binary) {
        if (binary) return "BINARY_FILE";
        String normalized = "/" + path.toLowerCase(Locale.ROOT).replace('\\', '/') + "/";
        if (GENERATED_SEGMENTS.stream().anyMatch(value -> normalized.contains("/" + value + "/")))
            return "GENERATED_OR_VENDOR_PATH";
        if (normalized.endsWith(".min.js/") || normalized.endsWith(".map/"))
            return "GENERATED_FILE";
        return null;
    }

    private String language(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) return "Java";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "TypeScript";
        if (lower.endsWith(".py")) return "Python";
        if (lower.endsWith(".md")) return "Markdown";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "YAML";
        if (lower.endsWith(".json")) return "JSON";
        if (lower.endsWith(".sql")) return "SQL";
        if (lower.endsWith(".xml")) return "XML";
        if (lower.endsWith(".scss") || lower.endsWith(".css")) return "CSS";
        return "Unknown";
    }

    private String category(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (isAdr(path)) return "ADR";
        if (isRoadmap(path)) return "ROADMAP";
        if (lower.contains("/test/") || lower.contains("/tests/")
                || lower.endsWith(".spec.ts") || lower.endsWith("test.java")) return "TEST";
        if (lower.endsWith(".md")) return "DOCUMENTATION";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")
                || lower.endsWith("dockerfile") || lower.endsWith(".properties")) return "CONFIGURATION";
        return "SOURCE";
    }

    private boolean isAdr(String path) {
        return path.toLowerCase(Locale.ROOT).matches(".*(?:^|/)adr[-_]?\\d+.*\\.md$")
                || path.toLowerCase(Locale.ROOT).contains("/decisions/adr-");
    }

    private boolean isRoadmap(String path) {
        return path.toLowerCase(Locale.ROOT).matches(".*(?:^|/)roadmap(?:\\.[^/]*)?$");
    }

    private String path(ChangedFile file) {
        return file.getNewPath() != null ? file.getNewPath() : file.getOldPath();
    }

    private String evidence(ProjectCommit commit, String path) {
        return "git:" + commit.getSource().getId() + ":" + commit.getCommitHash() + ":" + path;
    }
}
