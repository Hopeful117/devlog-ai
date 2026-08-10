package com.hopeful117.devlogai.knowledge.relation.repository;

import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KnowledgeRelationRepository extends JpaRepository<KnowledgeRelation, UUID> {

    List<KnowledgeRelation> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<KnowledgeRelation> findBySourceEntityTypeAndSourceEntityId(
            EntityType sourceEntityType,
            UUID sourceEntityId
    );

    List<KnowledgeRelation> findByTargetEntityTypeAndTargetEntityId(
            EntityType targetEntityType,
            UUID targetEntityId
    );
}
