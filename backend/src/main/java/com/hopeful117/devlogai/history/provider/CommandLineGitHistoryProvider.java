package com.hopeful117.devlogai.history.provider;

import com.hopeful117.devlogai.collection.workspace.GitCommandException;
import com.hopeful117.devlogai.collection.workspace.GitCommandExecutor;
import com.hopeful117.devlogai.history.model.DiffStatistics;
import com.hopeful117.devlogai.history.model.FileChangeType;
import com.hopeful117.devlogai.history.model.GitCommitData;
import com.hopeful117.devlogai.history.model.GitFileChange;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CommandLineGitHistoryProvider implements GitHistoryProvider {
    private static final String FIELD_SEPARATOR = "\u001f";
    private final GitCommandExecutor git;

    public CommandLineGitHistoryProvider(GitCommandExecutor git) {
        this.git = git;
    }

    @Override
    public List<GitCommitData> readHistory(Path repository, String revision) {
        try {
            String output = git.execute(repository, List.of("rev-list", "--reverse", revision));
            if (output.isBlank()) return List.of();
            List<GitCommitData> commits = new ArrayList<>();
            for (String hash : output.split("\\R")) commits.add(readCommit(repository, hash));
            return List.copyOf(commits);
        } catch (GitCommandException | IllegalArgumentException exception) {
            throw new GitHistoryReadException("Unable to read Git history for " + revision, exception);
        }
    }

    private GitCommitData readCommit(Path repository, String hash) {
        String metadata = git.execute(repository, List.of(
                "show", "-s", "--format=%H%x1f%P%x1f%an%x1f%ae%x1f%aI%x1f%cI%x1f%s%x1f%B", hash
        ));
        String[] fields = metadata.split(FIELD_SEPARATOR, 8);
        if (fields.length != 8) throw new GitHistoryReadException("Invalid Git metadata for " + hash);
        List<String> parents = fields[1].isBlank() ? List.of() : List.of(fields[1].split(" "));
        String base = parents.isEmpty() ? null : parents.getFirst();
        List<String> diffArguments = base == null
                ? new ArrayList<>(List.of("diff-tree", "--root", "--no-commit-id", "-r",
                        "--name-status", "-z", "-M", "-C", "--find-copies-harder", hash, "--"))
                : new ArrayList<>(List.of("diff", "--name-status", "-z", "-M", "-C",
                        "--find-copies-harder", base, hash, "--"));
        List<GitFileChange> changes = parseChanges(git.execute(repository, diffArguments));
        LineStatsResult lineStats = readLineStats(repository, base, hash);
        List<GitFileChange> enriched = changes.stream().map(change -> {
            String path = change.newPath() != null ? change.newPath() : change.oldPath();
            LineStats stats = lineStats.byPath().getOrDefault(path, LineStats.EMPTY);
            return new GitFileChange(change.changeType(), change.oldPath(), change.newPath(),
                    stats.binary(), stats.insertions(), stats.deletions());
        }).toList();
        int binaries = (int) enriched.stream().filter(GitFileChange::binary).count();
        return new GitCommitData(fields[0], parents, emptyToNull(fields[2]), emptyToNull(fields[3]),
                Instant.parse(fields[4]), Instant.parse(fields[5]), fields[6], fields[7].strip(),
                enriched, new DiffStatistics(enriched.size(), lineStats.insertions(),
                        lineStats.deletions(), binaries));
    }

    private List<GitFileChange> parseChanges(String output) {
        if (output.isBlank()) return List.of();
        String[] tokens = output.split("\u0000");
        List<GitFileChange> changes = new ArrayList<>();
        for (int index = 0; index < tokens.length;) {
            String status = tokens[index++];
            FileChangeType type = switch (status.charAt(0)) {
                case 'A' -> FileChangeType.ADDED;
                case 'D' -> FileChangeType.DELETED;
                case 'R' -> FileChangeType.RENAMED;
                case 'C' -> FileChangeType.COPIED;
                default -> FileChangeType.MODIFIED;
            };
            String first = tokens[index++];
            String second = (type == FileChangeType.RENAMED || type == FileChangeType.COPIED)
                    ? tokens[index++] : null;
            changes.add(new GitFileChange(type,
                    type == FileChangeType.ADDED ? null : first,
                    type == FileChangeType.DELETED ? null : (second == null ? first : second),
                    false, 0, 0));
        }
        return changes;
    }

    private LineStatsResult readLineStats(Path repository, String base, String hash) {
        List<String> arguments = base == null
                ? new ArrayList<>(List.of("diff-tree", "--root", "--no-commit-id", "-r",
                        "--numstat", "-M", "-C", hash, "--"))
                : new ArrayList<>(List.of("diff", "--numstat", "-M", "-C",
                        base, hash, "--"));
        String output = git.execute(repository, arguments);
        Map<String, LineStats> result = new HashMap<>();
        if (output.isBlank()) return new LineStatsResult(result, 0, 0);
        int totalInsertions = 0;
        int totalDeletions = 0;
        for (String line : output.split("\\R")) {
            String[] columns = line.split("\\t", 3);
            if (columns.length != 3) continue;
            boolean binary = columns[0].equals("-") || columns[1].equals("-");
            int insertions = binary ? 0 : Integer.parseInt(columns[0]);
            int deletions = binary ? 0 : Integer.parseInt(columns[1]);
            totalInsertions += insertions;
            totalDeletions += deletions;
            result.put(columns[2], new LineStats(binary, insertions, deletions));
        }
        return new LineStatsResult(result, totalInsertions, totalDeletions);
    }

    private String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }

    private record LineStats(boolean binary, int insertions, int deletions) {
        private static final LineStats EMPTY = new LineStats(false, 0, 0);
    }

    private record LineStatsResult(
            Map<String, LineStats> byPath,
            int insertions,
            int deletions
    ) {
    }
}
