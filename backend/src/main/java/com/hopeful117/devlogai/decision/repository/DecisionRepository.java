package com.hopeful117.devlogai.decision.repository;

import com.hopeful117.devlogai.decision.entity.Decision;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {
   List<Decision> findByProjectIdOrderByCreatedAtDesc(UUID uuid);

   List<Decision> findByProjectIdOrderByCreatedAtDescIdDesc(
            UUID projectId,
            Pageable pageable
    );

   List<Decision> findByProposalIdIn(Collection<UUID> proposalIds);

   Optional<Decision> findByProposalId(UUID proposalId);
}
