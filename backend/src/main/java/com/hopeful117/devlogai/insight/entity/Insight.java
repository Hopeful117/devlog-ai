package com.hopeful117.devlogai.insight.entity;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.validation.entity.Validation;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "insights")
@Getter
@EntityListeners(AuditingEntityListener.class)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Insight {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false, updatable = false, unique = true)
    private ValidatableProposal proposal;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "validation_id", nullable = false, updatable = false, unique = true)
    private Validation validation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InsightType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InsightSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "trust_state", nullable = false)
    @Builder.Default
    @Setter
    private InsightTrustState trustState = InsightTrustState.ACTIVE;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "TEXT")
    private String rationale;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_references", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> evidenceReferences = new ArrayList<>();

    @Column(name = "source_type")
    private String sourceType;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

}
