package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Produces commit-scoped {@link CollectedFact} items from persisted commit history.
 * <p>
 * These facts bridge the gap between repository evidence (file-level metadata)
 * and the grounding contract's {@code allowedSupportingFactIds}, which requires
 * UUID-traceable facts flowing through {@code KnowledgeSelectionService}.
 */
@Component
public class CommitScopedFactCollector implements KnowledgeCollector {

    private static final String VERSION = "commit-scoped-fact-v1";
    private static final int MAX_FACTS = 20;

    private final ProjectCommitRepository projectCommitRepository;
    private final int windowDays;

    public CommitScopedFactCollector(
            ProjectCommitRepository projectCommitRepository,
            @Value("${devlog.context.commit-diff.window-days:90}") int windowDays
    ) {
        this.projectCommitRepository = projectCommitRepository;
        this.windowDays = windowDays;
    }

    @Override
    public CollectorType type() {
        return CollectorType.COMMIT_SCOPED;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public CollectionResult collect(CollectionContext context) {
        Instant cutoff = context.collectionTimestamp().minus(Duration.ofDays(windowDays));
        List<ProjectCommit> commits = projectCommitRepository
                .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
                        context.projectId(), cutoff);

        if (commits.isEmpty()) {
            return CollectionResult.of(type(), version(), List.of(), List.of());
        }

        List<CollectedFact> facts = new ArrayList<>();
        Map<String, List<ChangedFile>> moduleFiles = new LinkedHashMap<>();
        AggregateStats stats = new AggregateStats();

        for (ProjectCommit commit : commits) {
            accumulateStats(commit, stats);
            groupFilesByModule(commit, moduleFiles);
            classifyCommit(commit, facts);
        }

        addAggregateFacts(commits, stats, moduleFiles, facts);

        return CollectionResult.of(type(), version(),
                facts.stream().limit(MAX_FACTS).toList(),
                List.of());
    }

    private void accumulateStats(ProjectCommit commit, AggregateStats stats) {
        stats.totalFiles += commit.getFilesChanged();
        stats.totalInsertions += commit.getInsertions();
        stats.totalDeletions += commit.getDeletions();
    }

    private void groupFilesByModule(ProjectCommit commit, Map<String, List<ChangedFile>> moduleFiles) {
        for (ChangedFile file : commit.getChangedFiles()) {
            String path = file.getNewPath() != null ? file.getNewPath() : file.getOldPath();
            if (path != null) {
                String module = extractModule(path);
                moduleFiles.computeIfAbsent(module, k -> new ArrayList<>()).add(file);
            }
        }
    }

    private void classifyCommit(ProjectCommit commit, List<CollectedFact> facts) {
        String subject = commit.getSubject() != null ? commit.getSubject() : "";
        String lowerSubject = subject.toLowerCase(Locale.ROOT);

        if (isFeatureCommit(lowerSubject)) {
            facts.add(createFact(FactType.COMMIT_ADDS_FEATURE, "Feature: " + subject, commit));
        }
        if (isBugFixCommit(lowerSubject)) {
            facts.add(createFact(FactType.COMMIT_FIXES_BUG, "Bug fix: " + subject, commit));
        }
        if (isRefactorCommit(lowerSubject)) {
            facts.add(createFact(FactType.COMMIT_REFACTORS_CODE, "Refactoring: " + subject, commit));
        }
    }

    private void addAggregateFacts(List<ProjectCommit> commits, AggregateStats stats,
                                   Map<String, List<ChangedFile>> moduleFiles, List<CollectedFact> facts) {
        facts.addFirst(createFact(
                FactType.COMMIT_DIFF_SUMMARY,
                commits.size() + " commits: " + stats.totalFiles + " files changed, "
                        + "+" + stats.totalInsertions + "/-" + stats.totalDeletions + " lines",
                commits.getFirst()
        ));

        for (Map.Entry<String, List<ChangedFile>> entry : moduleFiles.entrySet()) {
            facts.add(createFact(
                    FactType.COMMIT_CHANGES_MODULE,
                    "Module " + entry.getKey() + ": " + entry.getValue().size() + " files changed",
                    commits.getFirst()
            ));
        }
    }

    private CollectedFact createFact(FactType type, String content, ProjectCommit commit) {
        List<String> evidenceReferences = commit.getChangedFiles().stream()
                .map(f -> f.getNewPath() != null ? f.getNewPath() : f.getOldPath())
                .filter(p -> p != null)
                .distinct()
                .sorted()
                .toList();

        return CollectedFact.create(
                VERSION,
                type,
                content,
                evidenceReferences.isEmpty() ? List.of("commit:" + commit.getCommitHash()) : evidenceReferences,
                commit.getCommitHash()
        );
    }

    private String extractModule(String path) {
        String[] parts = path.split("/");
        if (parts.length >= 2) {
            return parts[0] + "/" + parts[1];
        }
        return parts[0];
    }

    private boolean isFeatureCommit(String subject) {
        return subject.startsWith("feat:") || subject.startsWith("feature:")
                || subject.startsWith("add:") || subject.startsWith("new:")
                || subject.contains("add feature") || subject.contains("new feature");
    }

    private boolean isBugFixCommit(String subject) {
        return subject.startsWith("fix:") || subject.startsWith("bugfix:")
                || subject.startsWith("hotfix:") || subject.startsWith("bug:")
                || subject.contains("fix bug") || subject.contains("fix issue")
                || subject.contains("resolve");
    }

    private boolean isRefactorCommit(String subject) {
        return subject.startsWith("refactor:") || subject.startsWith("cleanup:")
                || subject.startsWith("restructure:") || subject.startsWith("reorganize:")
                || subject.contains("refactor") || subject.contains("cleanup");
    }

    private static class AggregateStats {
        int totalFiles;
        int totalInsertions;
        int totalDeletions;
    }
}
