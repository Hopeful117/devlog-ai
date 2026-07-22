package com.hopeful117.devlogai.analysis.diagnostics.entity;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.collection.collector.CollectorType;
import com.hopeful117.devlogai.source.entity.Source;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "collection_warnings")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CollectionWarningEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false, updatable = false)
    private Analysis analysis;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", updatable = false)
    private Source source;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false)
    private CollectorType collectorType;
    @Column(nullable = false, length = 100, updatable = false)
    private String collectorVersion;
    @Column(nullable = false, length = 100, updatable = false)
    private String code;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false)
    private WarningSeverity severity;
    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String message;
    @Column(length = 2000, updatable = false)
    private String evidenceReference;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> metadata;
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;
}
