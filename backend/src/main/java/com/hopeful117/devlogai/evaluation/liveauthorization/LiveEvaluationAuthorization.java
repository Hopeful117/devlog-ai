package com.hopeful117.devlogai.evaluation.liveauthorization;

import com.hopeful117.devlogai.evaluation.livepreparation.LiveEvaluationPreparation;
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
@Table(name = "live_evaluation_authorizations")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LiveEvaluationAuthorization {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preparation_id", nullable = false, updatable = false)
    private LiveEvaluationPreparation preparation;

    @Column(name = "requester_id", nullable = false, length = 255, updatable = false)
    private String requesterId;

    @Column(name = "approver_id", length = 255, updatable = false)
    private String approverId;

    @Column(name = "idempotency_key_hash", nullable = false, length = 64, updatable = false)
    private String idempotencyKeyHash;

    @Column(name = "plan_digest", nullable = false, length = 64, updatable = false)
    private String planDigest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LiveEvaluationAuthorizationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "artifact_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> artifactSnapshot;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "consumption_key_hash", length = 64)
    private String consumptionKeyHash;

    @Column(name = "consumer_id", length = 255)
    private String consumerId;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
