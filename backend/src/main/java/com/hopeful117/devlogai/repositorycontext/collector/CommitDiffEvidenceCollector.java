package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.model.FileChangeType;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Produces {@code COMMIT_DIFF} evidence items from {@code ChangedFile} entities
 * stored in the database. This fills the gap where the COMMIT_DIFF layer is
 * defined in the architecture (enum, profiles, ranker) but no collector produces it.
 *
 * @see com.hopeful117.devlogai.history.context.CommitDiffContextBuilder for exclusion logic reference
 */
@Component
@Order(35)
public class CommitDiffEvidenceCollector implements RepositoryContextCollector {

    private static final Set<String> GENERATED_SEGMENTS = Set.of(
            "node_modules", "vendor", "target", "build", "dist",
            "coverage", ".venv", "venv"
    );

    private final ProjectCommitRepository projectCommitRepository;
    private final EvidenceFactory evidenceFactory;
    private final int maxItems;
    private final int windowDays;

    public CommitDiffEvidenceCollector(
            ProjectCommitRepository projectCommitRepository,
            EvidenceFactory evidenceFactory,
            @Value("${devlog.context.commit-diff.max-items:50}") int maxItems,
            @Value("${devlog.context.commit-diff.window-days:90}") int windowDays
    ) {
        this.projectCommitRepository = projectCommitRepository;
        this.evidenceFactory = evidenceFactory;
        this.maxItems = maxItems;
        this.windowDays = windowDays;
    }

    @Override
    public String collectorId() {
        return "commit-diff";
    }

    @Override
    public String collectorVersion() {
        return "v1";
    }

    @Override
    public List<RepositoryEvidence> collect(ContextRequest request) {
        UUID projectId = request.analysisContext().project().id();
        Instant cutoff = request.analysisContext().analysis().startedAt()
                .minus(Duration.ofDays(windowDays));

        List<ProjectCommit> commits =
                projectCommitRepository
                        .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                                projectId, cutoff);

        if (commits.isEmpty()) {
            return List.of();
        }

        // Flatten all changed files from commits, grouped by normalized path
        Map<String, FileGroup> groups = new LinkedHashMap<>();
        for (ProjectCommit commit : commits) {
            for (ChangedFile file : commit.getChangedFiles()) {
                String path = file.getNewPath() != null ? file.getNewPath() : file.getOldPath();
                if (path == null) {
                    continue;
                }
                if (isExcluded(path, file.isBinary())) {
                    continue;
                }
                groups.computeIfAbsent(path, k -> new FileGroup(k, commit))
                        .add(file, commit);
            }
        }

        if (groups.isEmpty()) {
            return List.of();
        }

        // Sort groups: occurredAt desc → (insertions+deletions) desc → path asc
        List<FileGroup> sortedGroups = new ArrayList<>(groups.values());
        sortedGroups.sort(Comparator
                .comparing((FileGroup g) -> g.mostRecentCommit.getCommittedAt()).reversed()
                .thenComparing(Comparator
                        .comparing((FileGroup g) -> g.totalInsertions + g.totalDeletions)
                        .reversed())
                .thenComparing((FileGroup g) -> g.path));

        List<RepositoryEvidence> evidence = new ArrayList<>();
        String repositoryId = projectId.toString();

        for (FileGroup group : sortedGroups) {
            String reference = "diff:" + group.mostRecentCommit.getCommitHash()
                    + ":" + group.path;
            String summary = formatSummary(
                    group.dominantChangeType,
                    group.path,
                    group.oldPath,
                    group.totalInsertions,
                    group.totalDeletions,
                    group.commitHashes.size(),
                    group.hasBinary);

            evidence.add(evidenceFactory.create(
                    metadata(),
                    RepositoryContextLayer.COMMIT_DIFF,
                    "CHANGED_FILE",
                    reference,
                    summary,
                    group.mostRecentCommit.getCommittedAt(),
                    group.commitHashes.stream().map(h -> "diff:" + h + ":" + group.path).toList(),
                    repositoryId,
                    group.path,
                    "commit-diff:" + group.path,
                    request.budget().maximumSummaryCharacters()
            ));
        }

        return evidence.stream().limit(maxItems).toList();
    }

    private EvidenceFactory.ContextRequestMetadata metadata() {
        return new EvidenceFactory.ContextRequestMetadata(
                collectorId(), collectorVersion(), "DETERMINISTIC_EXTRACTION");
    }

    private boolean isExcluded(String path, boolean binary) {
        if (binary) return true;
        String normalized = "/" + path.toLowerCase(Locale.ROOT).replace('\\', '/') + "/";
        if (GENERATED_SEGMENTS.stream().anyMatch(s -> normalized.contains("/" + s + "/"))) {
            return true;
        }
        if (normalized.endsWith(".min.js/") || normalized.endsWith(".map/")) {
            return true;
        }
        return false;
    }

    private String formatSummary(FileChangeType changeType, String path,
            String oldPath, int insertions, int deletions, int commitCount, boolean binary) {
        if (binary) return "Binary " + path;
        return switch (changeType) {
            case ADDED -> "Added " + path + " (+" + insertions + ")";
            case DELETED -> "Deleted " + path + " (-" + deletions + ")";
            case RENAMED -> "Renamed " + oldPath + " \u2192 " + path;
            case MODIFIED, COPIED -> {
                String base = "Modified " + path
                        + " (+" + insertions + "/-" + deletions + ")";
                yield commitCount > 1 ? base + " in " + commitCount + " commits" : base;
            }
        };
    }

    /**
     * Accumulates changed file data across multiple commits for the same normalized path.
     */
    private static class FileGroup {
        final String path;
        ProjectCommit mostRecentCommit;
        FileChangeType dominantChangeType;
        String oldPath;
        int totalInsertions;
        int totalDeletions;
        boolean hasBinary;
        final List<String> commitHashes = new ArrayList<>();

        FileGroup(String path, ProjectCommit firstCommit) {
            this.path = path;
            this.mostRecentCommit = firstCommit;
        }

        void add(ChangedFile file, ProjectCommit commit) {
            totalInsertions += file.getInsertions();
            totalDeletions += file.getDeletions();
            if (file.isBinary()) {
                hasBinary = true;
            }
            if (file.getOldPath() != null && oldPath == null) {
                oldPath = file.getOldPath();
            }
            // Commits are iterated in descending order of committedAt,
            // so the first seen change type is the most recent.
            if (dominantChangeType == null) {
                dominantChangeType = file.getChangeType();
            }
            if (!commitHashes.contains(commit.getCommitHash())) {
                commitHashes.add(commit.getCommitHash());
            }
            // Update most recent commit if this one is newer
            if (commit.getCommittedAt().isAfter(mostRecentCommit.getCommittedAt())) {
                mostRecentCommit = commit;
            }
        }
    }
}
