package com.hopeful117.devlogai.history.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "commit_parents", uniqueConstraints =
        @UniqueConstraint(name = "uk_commit_parent_index",
                columnNames = {"project_commit_id", "parent_index"}))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitParent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_commit_id", nullable = false)
    private ProjectCommit commit;
    @Column(name = "parent_index", nullable = false)
    private int parentIndex;
    @Column(name = "parent_hash", nullable = false, length = 64)
    private String parentHash;
}
