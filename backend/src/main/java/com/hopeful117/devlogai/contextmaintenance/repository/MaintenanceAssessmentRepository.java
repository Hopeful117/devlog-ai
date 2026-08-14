package com.hopeful117.devlogai.contextmaintenance.repository;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceAssessmentRepository extends JpaRepository<MaintenanceAssessment, UUID> {

    List<MaintenanceAssessment> findByProject_IdOrderByCreatedAtDescIdDesc(UUID projectId);

    List<MaintenanceAssessment> findByFinding_IdAndProject_IdOrderByCreatedAtDescIdDesc(
            UUID findingId,
            UUID projectId
    );

    Optional<MaintenanceAssessment> findByIdAndProject_Id(UUID id, UUID projectId);
}
