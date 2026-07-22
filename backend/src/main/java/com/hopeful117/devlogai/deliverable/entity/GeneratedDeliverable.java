package com.hopeful117.devlogai.deliverable.entity;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "generated_deliverables")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedDeliverable {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id")
    private Analysis analysis;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private DeliverableType type;
    @Column(nullable = false, length = 200) private String audience;
    @Column(nullable = false, length = 100) private String style;
    @Column(nullable = false, length = 20) private String language;
    @Column(length = 1000) private String additionalGuidance;
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(nullable = false, length = 100) private String promptVersion;
    @Column(nullable = false, length = 64) private String promptDigest;
    @Column(nullable = false, length = 100) private String provider;
    @Column(nullable = false, length = 255) private String modelIdentifier;
    @Column(nullable = false) private Instant generatedAt;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "generated_deliverable_insights",
            joinColumns = @JoinColumn(name = "deliverable_id"),
            inverseJoinColumns = @JoinColumn(name = "insight_id"))
    @Builder.Default
    private Set<Insight> sourceInsights = new LinkedHashSet<>();
}
