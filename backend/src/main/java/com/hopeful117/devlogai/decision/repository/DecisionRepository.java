package com.hopeful117.devlogai.decision.repository;

import com.hopeful117.devlogai.decision.entity.Decision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {
   List<Decision> findByProjectIdOrderByCreatedAtDesc(UUID uuid);
}
