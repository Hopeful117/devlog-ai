package com.hopeful117.devlogai.history.entity;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.source.entity.Source;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "project_commits", uniqueConstraints =
        @UniqueConstraint(name = "uk_project_commit_source_hash",
                columnNames = {"source_id", "commit_hash"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCommit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(name = "commit_hash", nullable = false, length = 64)
    private String commitHash;
    @Column(length = 255)
    private String authorName;
    @Column(length = 320)
    private String authorEmail;
    private Instant authoredAt;
    @Column(nullable = false)
    private Instant committedAt;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String subject;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String fullMessage;
    @Column(nullable = false)
    private boolean rootCommit;
    @Column(nullable = false)
    private boolean mergeCommit;
    @Column(nullable = false)
    private int filesChanged;
    @Column(nullable = false)
    private int insertions;
    @Column(nullable = false)
    private int deletions;
    @Column(nullable = false)
    private int binaryFiles;
    @Column(nullable = false, updatable = false)
    private Instant importedAt;

    @Builder.Default
    @OneToMany(mappedBy = "commit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("parentIndex ASC")
    private List<CommitParent> parents = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "commit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ChangedFile> changedFiles = new ArrayList<>();

    public void addParent(int index, String hash) {
        parents.add(CommitParent.builder().commit(this).parentIndex(index).parentHash(hash).build());
    }

    public void addChangedFile(ChangedFile file) {
        file.setCommit(this);
        changedFiles.add(file);
    }
}
