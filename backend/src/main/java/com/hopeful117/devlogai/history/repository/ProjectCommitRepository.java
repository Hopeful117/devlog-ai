package com.hopeful117.devlogai.history.repository;

import com.hopeful117.devlogai.history.entity.ProjectCommit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectCommitRepository extends JpaRepository<ProjectCommit, UUID> {
    boolean existsBySourceIdAndCommitHash(UUID sourceId, String commitHash);

    Optional<ProjectCommit> findBySourceIdAndCommitHash(UUID sourceId, String commitHash);

    List<ProjectCommit> findByProjectIdOrderByCommittedAtAscCommitHashAsc(UUID projectId);

    @EntityGraph(attributePaths = {"parents", "source"})
    List<ProjectCommit> findByProjectIdOrderByCommittedAtDescCommitHashDesc(
            UUID projectId, Pageable pageable);
}
