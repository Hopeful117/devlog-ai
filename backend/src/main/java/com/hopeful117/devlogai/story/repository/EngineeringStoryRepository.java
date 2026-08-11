package com.hopeful117.devlogai.story.repository;

import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EngineeringStoryRepository extends JpaRepository<EngineeringStory, UUID> {

    List<EngineeringStory> findByProject_Id(UUID projectId);

    List<EngineeringStory> findByProject_IdOrderByCreatedAtDesc(UUID projectId);

    List<EngineeringStory> findByProject_IdAndStatusOrderByCreatedAtDesc(
            UUID projectId,
            StoryStatus status
    );

    List<EngineeringStory> findByProject_IdAndStatusOrderByCompletedAtDescIdDesc(
            UUID projectId,
            StoryStatus status,
            Pageable pageable
    );
}