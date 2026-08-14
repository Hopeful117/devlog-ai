package com.hopeful117.devlogai.contextmaintenance.repository;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceAssessmentRepository extends JpaRepository<MaintenanceAssessment, UUID> {

    List<MaintenanceAssessment> findByProjectIdOrderByCreatedAtDescIdDesc(UUID projectId);

    List<MaintenanceAssessment> findByFinding_IdAndProjectIdOrderByCreatedAtDescIdDesc(
            UUID findingId,
            UUID projectId
    );

    Optional<MaintenanceAssessment> findByIdAndProjectId(UUID id, UUID projectId);

    List<MaintenanceAssessment> findByFindingIdInOrderByCreatedAtDescIdDesc(Collection<UUID> findingIds);
}
