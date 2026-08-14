package com.hopeful117.devlogai.contextmaintenance.entity;

import com.hopeful117.devlogai.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "maintenance_assessments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class MaintenanceAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false)
    private MaintenanceFinding finding;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false, length = 20)
    private MaintenanceAssessmentConfidenceLevel confidenceLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "semantic_classification", nullable = false, length = 40)
    private MaintenanceAssessmentSemanticClassification semanticClassification;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommended_action", nullable = false, length = 20)
    private MaintenanceAssessmentRecommendedAction recommendedAction;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "supporting_signals", columnDefinition = "TEXT")
    private String supportingSignals;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
