package com.hopeful117.devlogai.engineeringevent;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.source.entity.Source;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_evolution_scopes")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class AnalysisEvolutionScope implements Persistable<UUID> {
    public static final String CONTEXT_VERSION = "commit-evolution-context-v1";
    @Id private UUID analysisId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id") private Analysis analysis;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false) private Source source;
    @Column(nullable = false, length = 50) private String contextVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private EvolutionComparisonPolicy comparisonPolicy;
    @Column(nullable = false, length = 64) private String baseCommit;
    @Column(nullable = false, length = 64) private String targetCommit;
    @Column(nullable = false) private Instant targetCommittedAt;
    @Column(nullable = false) private boolean mergeCommit;

    @Override public UUID getId() { return analysisId; }
    @Override @Transient public boolean isNew() { return true; }
}
