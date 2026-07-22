package com.hopeful117.devlogai.analysis.diagnostics.entity;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "analysis_execution_diagnostics")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AnalysisExecutionDiagnostic {
    @Id private UUID analysisId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId
    @JoinColumn(name = "analysis_id") private Analysis analysis;
    private int sourceCount;
    private int factCount;
    private int observationCount;
    private int warningCount;
    private int errorCount;
    private int collectorCount;
    private int successfulCollectors;
    private int collectorsWithWarnings;
    private int failedCollectors;
    private boolean collectionComplete;
    private boolean truncated;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> resolvedRevisions;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> collectorVersions;
    @Column(nullable = false, updatable = false) private Instant collectedAt;
}
