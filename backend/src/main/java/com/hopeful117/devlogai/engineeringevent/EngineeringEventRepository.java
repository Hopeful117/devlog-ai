package com.hopeful117.devlogai.engineeringevent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface EngineeringEventRepository extends JpaRepository<EngineeringEvent, UUID> {
    @EntityGraph(attributePaths = {"project", "analysis", "proposal", "validation", "source"})
    Page<EngineeringEvent> findByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(
            UUID projectId, Pageable pageable);
    @EntityGraph(attributePaths = {"project", "analysis", "proposal", "validation", "source"})
    Optional<EngineeringEvent> findDetailedById(UUID id);
    List<EngineeringEvent> findByProposalIdIn(Collection<UUID> proposalIds);
    @EntityGraph(attributePaths = {"project", "analysis", "proposal", "validation", "source"})
    List<EngineeringEvent> findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(
            UUID projectId, Pageable pageable);

    @Query(value = """
            with event_references as (
                select e.id as "eventId",
                       e.source_id as "sourceId",
                       e.category as "category",
                       e.title as "title",
                       e.occurred_at as "occurredAt",
                       'REFERENCES_AS_BASE' as "role"
                from engineering_events e
                join sources s on s.id = e.source_id
                              and s.project_id = e.project_id
                where e.project_id = :projectId
                  and e.base_commit = :commitHash
                union all
                select e.id as "eventId",
                       e.source_id as "sourceId",
                       e.category as "category",
                       e.title as "title",
                       e.occurred_at as "occurredAt",
                       'REFERENCES_AS_TARGET' as "role"
                from engineering_events e
                join sources s on s.id = e.source_id
                              and s.project_id = e.project_id
                where e.project_id = :projectId
                  and e.target_commit = :commitHash
            )
            select "eventId", "sourceId", "category", "title", "occurredAt", "role"
            from event_references
            order by "occurredAt" desc, "eventId" asc, "role" asc
            limit :fetchLimit
            """, nativeQuery = true)
    List<EventCommitReferenceProjection> findCommitReferences(
            @Param("projectId") UUID projectId,
            @Param("commitHash") String commitHash,
            @Param("fetchLimit") int fetchLimit
    );

    interface EventCommitReferenceProjection {
        UUID getEventId();

        UUID getSourceId();

        String getCategory();

        String getTitle();

        java.time.Instant getOccurredAt();

        String getRole();
    }
}
