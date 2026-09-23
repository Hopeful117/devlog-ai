package com.hopeful117.devlogai.story.repository;

import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EngineeringStoryRepository extends JpaRepository<EngineeringStory, UUID> {

    @EntityGraph(attributePaths = "project")
    java.util.Optional<EngineeringStory> findDetailedById(UUID id);

    List<EngineeringStory> findByProject_Id(UUID projectId);

    @EntityGraph(attributePaths = "project")
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

    @Query(value = """
            with story_references as (
                select s.id as "storyId",
                       s.story_number as "storyNumber",
                       s.story_path as "storyPath",
                       s.status as "status",
                       s.created_at as "createdAt",
                       'REFERENCES_AS_BASE' as "role"
                from engineering_stories s
                where s.project_id = :projectId
                  and lower(trim(s.base_commit)) = :commitHash
                union all
                select s.id as "storyId",
                       s.story_number as "storyNumber",
                       s.story_path as "storyPath",
                       s.status as "status",
                       s.created_at as "createdAt",
                       'REFERENCES_AS_TARGET' as "role"
                from engineering_stories s
                where s.project_id = :projectId
                  and lower(trim(s.target_commit)) = :commitHash
            )
            select "storyId", "storyNumber", "storyPath", "status", "role"
            from story_references
            order by "createdAt" desc, "storyId" asc, "role" asc
            limit :fetchLimit
            """, nativeQuery = true)
    List<StoryCommitReferenceProjection> findCommitReferences(
            @Param("projectId") UUID projectId,
            @Param("commitHash") String commitHash,
            @Param("fetchLimit") int fetchLimit
    );

    interface StoryCommitReferenceProjection {
        UUID getStoryId();

        Integer getStoryNumber();

        String getStoryPath();

        String getStatus();

        String getRole();
    }
}
