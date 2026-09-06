package com.hopeful117.devlogai.storycontextanalysis.repository;

import com.hopeful117.devlogai.storycontextanalysis.entity.StoryContextAnalysis;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoryContextAnalysisRepository extends JpaRepository<StoryContextAnalysis, UUID> {
    List<StoryContextAnalysis> findByStoryOrderByCreatedAtDesc(EngineeringStory story);
    Optional<StoryContextAnalysis> findByAiTaskId(UUID aiTaskId);
}