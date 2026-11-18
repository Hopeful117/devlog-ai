package com.hopeful117.devlogai.knowledge.repository;


import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface KnowledgeEventRepository extends JpaRepository<KnowledgeEvent, UUID> {
    List<KnowledgeEvent> findByProjectId(UUID projectId);
    List<KnowledgeEvent> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<KnowledgeEvent> findByProjectIdOrderByCreatedAtDescIdDesc(
            UUID projectId,
            Pageable pageable
    );

    @Query(value = """
            SELECT * FROM knowledge_events
            WHERE project_id = :projectId
              AND search_vector @@ plainto_tsquery('english', :query)
            ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<KnowledgeEvent> findByProjectIdAndFullText(
            @Param("projectId") UUID projectId,
            @Param("query") String query,
            @Param("limit") int limit
    );
}
