package com.hopeful117.devlogai.contextmaintenance.entity;

import com.hopeful117.devlogai.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "maintenance_findings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class MaintenanceFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_surface", nullable = false, length = 50)
    private MaintenanceContextSurface contextSurface;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 80)
    private MaintenanceFindingIssueType issueType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaintenanceFindingSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaintenanceFindingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_action", nullable = false, length = 20)
    private MaintenanceSuggestedActionCategory suggestedAction;

    @Column(name = "human_review_required", nullable = false)
    private boolean humanReviewRequired;

    @Column(nullable = false, length = 255)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String details;

    @OneToMany(mappedBy = "finding", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("actedAt DESC, id DESC")
    @Builder.Default
    private List<MaintenanceFindingAction> actions = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
