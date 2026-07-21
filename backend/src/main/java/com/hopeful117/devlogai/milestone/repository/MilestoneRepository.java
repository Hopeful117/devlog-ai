package com.hopeful117.devlogai.milestone.repository;

import com.hopeful117.devlogai.milestone.entity.Milestone;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

    List<Milestone> findByProjectIdOrderByStartedAtDesc(
            UUID projectId
    );

    List<Milestone> findByProjectIdAndStatusOrderByStartedAtDesc(
            UUID projectId,
            MilestoneStatus status
    );
}
