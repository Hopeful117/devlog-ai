package com.hopeful117.devlogai.profile.entity;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus;
import com.hopeful117.devlogai.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "project_profile_snapshots")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProjectProfileSnapshot {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "analysis_id", nullable = false, unique = true, updatable = false)
    private Analysis analysis;
    @Column(nullable = false, updatable = false) private String profileVersion;
    @Column(nullable = false, updatable = false) private String rendererVersion;
    @Column(nullable = false, updatable = false) private Instant generatedAt;
    @Column(updatable = false) private String requestedRevision;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> resolvedRevisions;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false)
    private ProfileCompletenessStatus completenessStatus;
    @Column(nullable = false, updatable = false) private boolean collectionComplete;
    @Column(nullable = false, updatable = false) private boolean truncated;
    @Column(nullable = false, updatable = false) private int warningCount;
    @Column(nullable = false, updatable = false) private int errorCount;
    @Column(nullable = false, updatable = false) private int successfulCollectorCount;
    @Column(nullable = false, updatable = false) private int collectorsWithWarningsCount;
    @Column(nullable = false, updatable = false) private int failedCollectorCount;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private List<Map<String, Object>> sections;
    @Column(nullable = false, columnDefinition = "text", updatable = false) private String deterministicSummary;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private List<Map<String, Object>> sourceObservations;
    @Column(nullable = false, updatable = false) private int characteristicCount;
}
