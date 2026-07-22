package com.hopeful117.devlogai.analysis.entity;

import com.hopeful117.devlogai.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;

@Entity
@Table(name = "analyses")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Analysis {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisType type;

    @Column(name = "intent_id", length = 80, updatable = false)
    private String intentId;

    @Column(name = "intent_version", length = 20, updatable = false)
    private String intentVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_guidance", updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> userGuidance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    @Column(length = 255, updatable = false)
    private String targetRevision;

    // An analysis is pending until deterministic processing starts.
    @Column
    private Instant startedAt;

    private Instant completedAt;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(nullable = false)
    @LastModifiedDate
    private Instant updatedAt;
}
