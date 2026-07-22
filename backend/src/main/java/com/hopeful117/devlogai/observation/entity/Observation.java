package com.hopeful117.devlogai.observation.entity;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.fact.entity.Fact;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "observations")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Observation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ObservationType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "rule_id", nullable = false, length = 100)
    private String ruleId;

    @Column(name = "rule_version", nullable = false, length = 50)
    private String ruleVersion;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "observation_facts",
            joinColumns = @JoinColumn(name = "observation_id"),
            inverseJoinColumns = @JoinColumn(name = "fact_id")
    )
    @Builder.Default
    private Set<Fact> supportingFacts = new LinkedHashSet<>();

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;
}
