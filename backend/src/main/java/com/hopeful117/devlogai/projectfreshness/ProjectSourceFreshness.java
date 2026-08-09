package com.hopeful117.devlogai.projectfreshness;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.source.entity.Source;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_source_freshness")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class ProjectSourceFreshness {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false, unique = true)
    private Source source;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "baseline_analysis_id")
    private Analysis baselineAnalysis;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private ProjectFreshnessStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private ProjectRefreshGuidance guidance;
    @Column(nullable = false, length = 300)
    private String requestedRevision;
    @Column(nullable = false, length = 64)
    private String currentRevision;
    @Column(length = 64)
    private String baselineRevision;
    @Column(nullable = false)
    private Instant checkedAt;
}
