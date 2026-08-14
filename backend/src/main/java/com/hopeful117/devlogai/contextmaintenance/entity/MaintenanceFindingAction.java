package com.hopeful117.devlogai.contextmaintenance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "maintenance_finding_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class MaintenanceFindingAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false)
    private MaintenanceFinding finding;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private MaintenanceFindingActionType actionType;

    @Column(name = "acted_by", nullable = false)
    private UUID actedBy;

    @CreatedDate
    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt;

    @Column(columnDefinition = "TEXT")
    private String comment;
}
