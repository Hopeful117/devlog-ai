package com.hopeful117.devlogai.history.service;

import com.hopeful117.devlogai.contracts.projecthistory.ProjectHistoryCommitMatch;
import com.hopeful117.devlogai.contracts.projecthistory.ProjectHistoryMatch;
import com.hopeful117.devlogai.contracts.projecthistory.ProjectHistoryMatchedOn;
import com.hopeful117.devlogai.contracts.projecthistory.ProjectHistorySearchResult;
import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.shared.exception.InvalidParameterException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic token search over the project history already imported by
 * DevLog: commit messages (subject + body) and changed paths. AND semantics
 * across query terms; textual/historical relevance first — recency is only a
 * tie-breaker so old relevant commits are never hidden by recent noise.
 */
@Service
@RequiredArgsConstructor
public class ProjectHistorySearchServiceImpl
        implements ProjectHistorySearchService, ProjectHistoryQueryService {

    /** Term fully equals a changed file name (e.g. {@code RepositoryContextEngine.java}). */
    static final int STRENGTH_FILENAME_EXACT = 30;
    /** Term contained in a changed path. */
    static final int STRENGTH_PATH = 20;
    /** Term contained in the commit subject. */
    static final int STRENGTH_SUBJECT = 15;
    /** Term contained in the commit message body. */
    static final int STRENGTH_MESSAGE = 10;

    private static final int MIN_TERM_LENGTH = 2;
    private static final int MAX_MATCHES_PER_COMMIT = 8;
    private static final int MAX_MATCHED_VALUE_LENGTH = 120;

    private final ProjectCommitRepository commitRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional(readOnly = true)
    public ProjectHistorySearchResult search(UUID projectId, String query, Integer limit) {
        validateQuery(query);
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
            throw new InvalidParameterException("limit",
                    "must be between 1 and %d".formatted(MAX_LIMIT));
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectId));

        List<ProjectHistoryQueryMatch> candidates = findMatches(projectId, null, query);
        // Preserve the existing public endpoint's tie ordering.
        candidates = candidates.stream()
                .sorted(Comparator.comparingInt(ProjectHistoryQueryMatch::relevance).reversed()
                        .thenComparing(ProjectHistoryQueryMatch::committedAt,
                                Comparator.reverseOrder())
                        .thenComparing(ProjectHistoryQueryMatch::commitSha))
                .toList();

        List<ProjectHistoryCommitMatch> results = candidates.stream()
                .limit(effectiveLimit)
                .map(candidate -> toMatch(candidate, project.getSlug()))
                .toList();

        return new ProjectHistorySearchResult(query.strip(), candidates.size(),
                candidates.size() > results.size(), results);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectHistoryQueryMatch> findMatches(
            UUID projectId, UUID sourceId, String query) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectId));
        validateQuery(query);
        List<String> terms = tokenize(query);

        List<Candidate> candidates = new ArrayList<>();
        for (ProjectCommit commit : commitRepository
                .findByProjectIdOrderByCommittedAtAscCommitHashAsc(projectId)) {
            if (!belongsToProject(commit, projectId)) continue;
            if (sourceId != null && !sourceId.equals(commit.getSource().getId())) continue;
            Candidate candidate = match(commit, terms);
            if (candidate != null) candidates.add(candidate);
        }

        return candidates.stream()
                .sorted(Comparator.comparingInt(Candidate::relevance).reversed()
                        .thenComparing(candidate -> candidate.commit().getCommittedAt(),
                                Comparator.reverseOrder())
                        .thenComparing(candidate -> candidate.commit().getSource().getId())
                        .thenComparing(candidate -> candidate.commit().getCommitHash()))
                .map(this::toQueryMatch)
                .toList();
    }

    private boolean belongsToProject(ProjectCommit commit, UUID projectId) {
        return commit.getSource() != null
                && commit.getSource().getProject() != null
                && projectId.equals(commit.getSource().getProject().getId());
    }

    private record Candidate(ProjectCommit commit, int relevance,
                             Map<ProjectHistoryMatchedOn, LinkedHashSet<String>> matchesByField) {
    }

    private Candidate match(ProjectCommit commit, List<String> terms) {
        String subjectLower = lower(commit.getSubject());
        String messageLower = lower(commit.getFullMessage());
        List<String> paths = changedPaths(commit);

        int relevance = 0;
        Map<ProjectHistoryMatchedOn, LinkedHashSet<String>> matches =
                new LinkedHashMap<>();
        for (String term : terms) {
            int strength = 0;
            String filenamePath = null;
            String pathMatch = null;
            for (String path : paths) {
                if (fileBaseName(path).equalsIgnoreCase(term)) {
                    strength = Math.max(strength, STRENGTH_FILENAME_EXACT);
                    filenamePath = path;
                    break;
                }
                if (path.toLowerCase(Locale.ROOT).contains(term)) {
                    strength = Math.max(strength, STRENGTH_PATH);
                    pathMatch = pathMatch == null ? path : pathMatch;
                }
            }
            boolean subjectMatches = subjectLower != null
                    && subjectLower.contains(term);
            boolean messageMatches = messageLower != null
                    && messageLower.contains(term);
            if (subjectMatches || messageMatches) {
                strength = Math.max(strength,
                        subjectMatches ? STRENGTH_SUBJECT : STRENGTH_MESSAGE);
            }
            if (strength == 0) return null;
            relevance += strength;
            collect(matches, ProjectHistoryMatchedOn.PATH, filenamePath);
            collect(matches, ProjectHistoryMatchedOn.PATH, pathMatch);
            collect(matches, ProjectHistoryMatchedOn.COMMIT_MESSAGE,
                    subjectMatches ? commit.getSubject()
                            : excerpt(messageLower == null ? null : messageLower, term));
        }
        return new Candidate(commit, relevance, matches);
    }

    private void collect(
            Map<ProjectHistoryMatchedOn, LinkedHashSet<String>> matches,
            ProjectHistoryMatchedOn field, String value) {
        if (value == null || value.isBlank()) return;
        Set<String> values = matches.computeIfAbsent(field,
                ignored -> new LinkedHashSet<>());
        if (values.size() >= MAX_MATCHES_PER_COMMIT) return;
        values.add(truncate(value));
    }

    private List<String> changedPaths(ProjectCommit commit) {
        List<String> paths = new ArrayList<>();
        for (ChangedFile file : commit.getChangedFiles()) {
            String path = file.getNewPath() != null ? file.getNewPath()
                    : file.getOldPath();
            if (path != null) paths.add(path);
        }
        return paths;
    }

    /** File name without its extension: {@code RepositoryContextEngine.java} → {@code repositorycontextengine}. */
    private static String fileBaseName(String path) {
        String name = fileName(path);
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String excerpt(String text, String term) {
        if (text == null) return null;
        int at = text.indexOf(term);
        if (at < 0) return null;
        int start = Math.max(0, at - 40);
        String window = text.substring(start,
                Math.min(text.length(), at + term.length() + 80)).strip();
        return (start > 0 ? "…" : "") + window + "…";
    }

    private static String truncate(String value) {
        return value.length() <= MAX_MATCHED_VALUE_LENGTH ? value
                : value.substring(0, MAX_MATCHED_VALUE_LENGTH - 3) + "…";
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) return List.of();
        String normalized = query.toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(normalized.split("[^a-z0-9]+"))
                .filter(term -> term.length() >= MIN_TERM_LENGTH)
                .distinct()
                .toList();
    }

    private static void validateQuery(String query) {
        if (tokenize(query).isEmpty()) {
            throw new InvalidParameterException("query",
                    "must contain at least one alphanumeric term of %d+ characters"
                            .formatted(MIN_TERM_LENGTH));
        }
    }

    private ProjectHistoryQueryMatch toQueryMatch(Candidate candidate) {
        ProjectCommit commit = candidate.commit();
        List<ProjectHistoryMatch> matches = new ArrayList<>();
        candidate.matchesByField().forEach((field, values) -> values.forEach(
                value -> matches.add(new ProjectHistoryMatch(field, value))));
        return new ProjectHistoryQueryMatch(commit.getCommitHash(), commit.getSubject(),
                commit.getAuthorName(), commit.getCommittedAt(), commit.getSource().getId(),
                candidate.relevance(), matches);
    }

    private ProjectHistoryCommitMatch toMatch(ProjectHistoryQueryMatch candidate, String slug) {
        return new ProjectHistoryCommitMatch(
                candidate.commitSha(), candidate.subject(), candidate.authorName(), candidate.committedAt(),
                candidate.sourceId(), candidate.relevance(), candidate.matches(),
                resourceUri(slug, candidate.commitSha()));
    }

    /** Fail-safe: a corrupted SHA yields no resource instead of failing the search. */
    private static String resourceUri(String slug, String commitHash) {
        try {
            return com.hopeful117.devlogai.contracts.engineeringcontext.DevlogResourceUriFactory
                    .commit(slug, commitHash);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
