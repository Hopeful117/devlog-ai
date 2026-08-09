package com.hopeful117.devlogai.engineeringevent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface EngineeringEventRepository extends JpaRepository<EngineeringEvent, UUID> {
    @EntityGraph(attributePaths = {"project", "analysis", "proposal", "validation", "source"})
    Page<EngineeringEvent> findByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(
            UUID projectId, Pageable pageable);
    @EntityGraph(attributePaths = {"project", "analysis", "proposal", "validation", "source"})
    Optional<EngineeringEvent> findDetailedById(UUID id);
    List<EngineeringEvent> findByProposalIdIn(Collection<UUID> proposalIds);
    List<EngineeringEvent> findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(
            UUID projectId, Pageable pageable);
}
