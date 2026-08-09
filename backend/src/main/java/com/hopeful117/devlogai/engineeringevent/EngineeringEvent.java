package com.hopeful117.devlogai.engineeringevent;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.validation.entity.Validation;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "engineering_events")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class EngineeringEvent {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false) private Analysis analysis;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false, unique = true) private ValidatableProposal proposal;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "validation_id", nullable = false, unique = true) private Validation validation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false) private Source source;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50)
    private EngineeringEventCategory category;
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String summary;
    @Column(nullable = false, columnDefinition = "TEXT") private String significance;
    @Column(nullable = false, length = 64) private String baseCommit;
    @Column(nullable = false, length = 64) private String targetCommit;
    @Column(nullable = false) private Instant occurredAt;
    @Column(nullable = false) private Instant createdAt;
}
