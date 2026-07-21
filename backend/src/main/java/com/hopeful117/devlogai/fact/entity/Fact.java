package com.hopeful117.devlogai.fact.entity;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "facts")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Fact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FactType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private String source;

    @ElementCollection
    @CollectionTable(
            name = "fact_evidence_references",
            joinColumns = @JoinColumn(name = "fact_id")
    )
    @Column(name = "reference", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private Set<String> evidenceReferences = new LinkedHashSet<>();

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant detectedAt;
}
