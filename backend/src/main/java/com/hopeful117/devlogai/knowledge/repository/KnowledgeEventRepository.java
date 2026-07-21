package com.hopeful117.devlogai.knowledge.repository;


import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KnowledgeEventRepository extends JpaRepository<KnowledgeEvent, UUID> {
    List<KnowledgeEvent> findByProjectId(UUID projectId);
    List<KnowledgeEvent> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<KnowledgeEvent> findByProjectIdOrderByCreatedAtDescIdDesc(
            UUID projectId,
            Pageable pageable
    );

}
