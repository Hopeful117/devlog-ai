package com.hopeful117.devlogai.validation.entity;

import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "validations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_validation_proposal_id",
                        columnNames = "proposal_id"
                )
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Validation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "proposal_id",
            nullable = false,
            unique = true
    )
    private ValidatableProposal proposal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ValidationDecision decision;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant validatedAt;

    @Column(nullable = false)
    private UUID validatedBy;

    @Column(columnDefinition = "TEXT")
    private String comment;
}
