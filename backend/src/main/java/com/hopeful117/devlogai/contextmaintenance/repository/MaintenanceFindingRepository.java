package com.hopeful117.devlogai.contextmaintenance.repository;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFinding;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceFindingRepository extends JpaRepository<MaintenanceFinding, UUID> {

    List<MaintenanceFinding> findByProject_IdOrderByCreatedAtDescIdDesc(UUID projectId);

    List<MaintenanceFinding> findByProject_IdAndStatusOrderByCreatedAtDescIdDesc(
            UUID projectId,
            MaintenanceFindingStatus status
    );

    Optional<MaintenanceFinding> findByIdAndProject_Id(UUID id, UUID projectId);
}
