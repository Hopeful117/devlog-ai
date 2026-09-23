package com.hopeful117.devlogai.history.repository;

import com.hopeful117.devlogai.history.entity.ProjectCommit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectCommitRepository extends JpaRepository<ProjectCommit, UUID> {
    boolean existsBySourceIdAndCommitHash(UUID sourceId, String commitHash);

    @EntityGraph(attributePaths = {"source", "changedFiles", "parents"})
    Optional<ProjectCommit> findBySourceIdAndCommitHash(UUID sourceId, String commitHash);

    List<ProjectCommit> findByProjectIdOrderByCommittedAtAscCommitHashAsc(UUID projectId);

    @EntityGraph(attributePaths = {"parents", "source"})
    List<ProjectCommit> findByProjectIdOrderByCommittedAtDescCommitHashDesc(
            UUID projectId, Pageable pageable);

    @EntityGraph(attributePaths = {"source", "changedFiles"})
    @Query("select c from ProjectCommit c where c.project.id = :projectId "
            + "order by c.committedAt desc, c.commitHash desc")
    List<ProjectCommit> findRecentWithChangedFiles(@Param("projectId") UUID projectId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"changedFiles"})
    List<ProjectCommit> findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(
            UUID projectId, java.time.Instant after);

    Optional<ProjectCommit> findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(
            UUID sourceId);
}
