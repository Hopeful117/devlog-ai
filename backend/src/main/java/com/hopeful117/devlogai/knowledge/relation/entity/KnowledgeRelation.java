package com.hopeful117.devlogai.knowledge.relation.entity;

import com.hopeful117.devlogai.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "knowledge_relations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_entity_type", nullable = false)
    private EntityType sourceEntityType;

    @Column(name = "source_entity_id", nullable = false)
    private UUID sourceEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_entity_type", nullable = false)
    private EntityType targetEntityType;

    @Column(name = "target_entity_id", nullable = false)
    private UUID targetEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false)
    private KnowledgeRelationType relationType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
