package com.hopeful117.devlogai.repositorysync;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.source.entity.Source;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositorySyncJobRepository extends JpaRepository<RepositorySyncJob, UUID> {

    Optional<RepositorySyncJob> findTopByProjectIdAndSourceIdOrderByCreatedAtDesc(UUID projectId, UUID sourceId);

    List<RepositorySyncJob> findByProjectIdAndSourceId(UUID projectId, UUID sourceId);

    List<RepositorySyncJob> findByProjectIdAndSourceIdAndStatus(UUID projectId, UUID sourceId, RepositorySyncJob.SyncStatus status);

    boolean existsBySourceIdAndStatusIn(UUID sourceId, List<RepositorySyncJob.SyncStatus> statuses);

    List<RepositorySyncJob> findByStatusOrderByCreatedAtAsc(RepositorySyncJob.SyncStatus status);

    List<RepositorySyncJob> findByStatus(RepositorySyncJob.SyncStatus status);

    long countByProjectIdAndSourceIdAndStatus(UUID projectId, UUID sourceId, RepositorySyncJob.SyncStatus status);
}