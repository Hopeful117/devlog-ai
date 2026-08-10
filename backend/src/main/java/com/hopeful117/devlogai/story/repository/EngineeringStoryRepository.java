package com.hopeful117.devlogai.story.repository;

import com.hopeful117.devlogai.story.entity.EngineeringStory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EngineeringStoryRepository extends JpaRepository<EngineeringStory, UUID> {

    List<EngineeringStory> findByProject_Id(UUID projectId);

    List<EngineeringStory> findByProject_IdOrderByCreatedAtDesc(UUID projectId);
}