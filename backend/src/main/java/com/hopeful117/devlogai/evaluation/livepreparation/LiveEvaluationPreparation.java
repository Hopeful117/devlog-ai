package com.hopeful117.devlogai.evaluation.livepreparation;

import com.hopeful117.devlogai.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "live_evaluation_preparations")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LiveEvaluationPreparation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(name = "principal_id", nullable = false, length = 255, updatable = false)
    private String principalId;

    @Column(name = "idempotency_key_hash", nullable = false, length = 64, updatable = false)
    private String idempotencyKeyHash;

    @Column(name = "idempotency_scope_hash", nullable = false, length = 64, updatable = false)
    private String idempotencyScopeHash;

    @Column(name = "request_digest", nullable = false, length = 64, updatable = false)
    private String requestDigest;

    @Column(name = "plan_digest", nullable = false, length = 64, updatable = false)
    private String planDigest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private LiveEvaluationPreparationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> planSnapshot;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;
}
