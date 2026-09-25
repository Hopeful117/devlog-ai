package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.model.FileChangeType;

import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

/** Reconstructs bounded repository relationships from persisted history data. */
public class RepositoryRelationshipProjector {

    private static final java.util.regex.Pattern GIT_OBJECT_ID =
            java.util.regex.Pattern.compile("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}");

    public List<EngineeringRelationship> project(ProjectCommit commit) {
        if (commit == null || commit.getProject() == null || commit.getSource() == null
                || commit.getCommitHash() == null || commit.getChangedFiles() == null) return List.of();
        return commit.getChangedFiles().stream()
                .flatMap(file -> projectFile(commit, file).stream())
                .sorted(Comparator.comparing(EngineeringRelationship::id))
                .toList();
    }

    /** Projects a Story or Engineering Event against the persisted Git window (base,target]. */
    public List<EngineeringRelationship> projectForEntity(
            String entityKind, UUID entityId, UUID projectId, UUID sourceId,
            String baseCommit, String targetCommit, List<ProjectCommit> commits) {
        if (entityKind == null || entityKind.isBlank() || entityId == null || projectId == null || sourceId == null
                || baseCommit == null || targetCommit == null || baseCommit.isBlank() || targetCommit.isBlank()
                || !validGitObjectId(baseCommit) || !validGitObjectId(targetCommit)
                || baseCommit.equalsIgnoreCase(targetCommit) || commits == null) return List.of();
        Map<String, ProjectCommit> byHash = new HashMap<>();
        for (ProjectCommit c : commits) {
            if (c == null || c.getCommitHash() == null || c.getProject() == null || c.getSource() == null
                    || c.getParents() == null) continue;
            if (!projectId.equals(c.getProject().getId()) || !sourceId.equals(c.getSource().getId())) continue;
            if (!validGitObjectId(c.getCommitHash())) return List.of();
            String hash = c.getCommitHash().toLowerCase(Locale.ROOT);
            if (byHash.putIfAbsent(hash, c) != null) return List.of();
        }
        String base = baseCommit.toLowerCase(Locale.ROOT), target = targetCommit.toLowerCase(Locale.ROOT);
        if (!byHash.containsKey(base) || !byHash.containsKey(target)) return List.of();
        Set<String> ancestorsTarget = ancestorsStrict(target, byHash);
        if (ancestorsTarget == null || !ancestorsTarget.contains(base)) return List.of();
        Set<String> ancestorsBase = ancestorsStrict(base, byHash);
        if (ancestorsBase == null) return List.of();
        Set<String> window = new HashSet<>(ancestorsTarget);
        window.removeAll(ancestorsBase);
        return window.stream().map(byHash::get).filter(java.util.Objects::nonNull)
                .flatMap(c -> project(c).stream()).map(r -> new EngineeringRelationship(
                        "changes:" + entityKind.toLowerCase() + ":" + entityId + ":" + r.target().canonicalIdentity(),
                        new EngineeringRelationship.RepositoryEntityEndpoint(entityKind, entityId), r.relationType(),
                        r.target(), r.origin(), r.trustTier(), r.revision(), r.evidenceReferences()))
                .sorted(Comparator.comparing(EngineeringRelationship::id)).toList();
    }

    private java.util.Set<String> ancestorsStrict(String start, java.util.Map<String, ProjectCommit> commits) {
        var seen = new java.util.HashSet<String>(); var queue = new java.util.ArrayDeque<String>();
        seen.add(start); queue.add(start);
        while (!queue.isEmpty()) { var c = commits.get(queue.remove()); if (c == null || c.getParents() == null) return null;
            for (var parent : c.getParents()) {
                if (parent == null || !validGitObjectId(parent.getParentHash())) return null;
                String parentHash = parent.getParentHash().toLowerCase(Locale.ROOT);
                if (seen.add(parentHash)) queue.add(parentHash);
            }
            for (String parent : new java.util.ArrayList<>(seen)) {
                if (!commits.containsKey(parent)) return null;
            }
        }
        return seen;
    }

    private boolean validGitObjectId(String hash) {
        return hash != null && GIT_OBJECT_ID.matcher(hash).matches();
    }

    private List<EngineeringRelationship> projectFile(ProjectCommit commit, ChangedFile file) {
        if (file == null) return List.of();
        List<String> paths = new java.util.ArrayList<>();
        if (file.getOldPath() != null && file.getChangeType() == FileChangeType.RENAMED) paths.add(file.getOldPath());
        if (file.getNewPath() != null) paths.add(file.getNewPath());
        if (paths.isEmpty() && file.getOldPath() != null) paths.add(file.getOldPath());
        return paths.stream().map(path -> project(commit, path)).filter(java.util.Objects::nonNull).toList();
    }

    private EngineeringRelationship project(ProjectCommit commit, String rawPath) {
        String path = rawPath;
        path = normalizePath(path);
        if (path == null) return null;
        UUID projectId = commit.getProject().getId();
        UUID sourceId = commit.getSource().getId();
        String revision = commit.getCommitHash();
        var source = new EngineeringRelationship.RepositoryCommitEndpoint(
                projectId, sourceId, revision);
        var target = new EngineeringRelationship.RepositoryFileEndpoint(
                projectId, sourceId, revision, path.replace('\\', '/'));
        return new EngineeringRelationship(
                "changes:" + source.canonicalIdentity() + ":" + target.canonicalIdentity(),
                source,
                "CHANGES",
                target,
                EngineeringRelationship.Origin.REPOSITORY_DERIVED,
                TrustTier.TECHNICAL_EVIDENCE,
                revision,
                List.of("git:" + sourceId + ":" + revision,
                        "diff:" + sourceId + ":" + revision + ":" + path));
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) return null;
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..") || normalized.contains(":")
                || normalized.endsWith("/")) return null;
        return normalized;
    }
}
