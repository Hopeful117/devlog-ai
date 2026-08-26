package com.hopeful117.devlogai.repositorysync;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessStatus;
import com.hopeful117.devlogai.source.entity.Source;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repository_sync_jobs")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class RepositorySyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(name = "from_revision", length = 64)
    private String fromRevision;

    @Column(name = "to_revision", length = 64, nullable = false)
    private String toRevision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SyncReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyncStatus status;

    private int attempt = 1;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    @Column(name = "failure", columnDefinition = "TEXT")
    private String failure;

    public boolean isCompleted() {
        return SyncStatus.COMPLETED.equals(status) || SyncStatus.FAILED.equals(status);
    }

    public boolean isRunning() {
        return SyncStatus.RUNNING.equals(status);
    }

    public boolean isPending() {
        return SyncStatus.PENDING.equals(status);
    }

    public enum SyncReason {
        REPOSITORY_CHANGE_DETECTED,
        MANUAL_SYNC,
        RECOVERY,
        INITIAL_IMPORT
    }

    public enum SyncStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}