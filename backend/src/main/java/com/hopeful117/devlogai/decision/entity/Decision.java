package com.hopeful117.devlogai.decision.entity;

import com.hopeful117.devlogai.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "decisions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "project_id",
            nullable = false
    )
    private Project project;


    @Column(nullable = false)
    private String title;


    @Column(length = 5000, nullable = false)
    private String context;


    @Column(length = 5000, nullable = false)
    private String choice;


    @Column(length = 5000, nullable = false)
    private String rationale;


    @Column(length = 5000)
    private String consequences;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}