package com.hopeful117.devlogai.history.entity;

import com.hopeful117.devlogai.history.model.FileChangeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "commit_changed_files")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangedFile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_commit_id", nullable = false)
    private ProjectCommit commit;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileChangeType changeType;
    @Column(length = 2000)
    private String oldPath;
    @Column(length = 2000)
    private String newPath;
    @Column(name = "binary_file", nullable = false)
    private boolean binary;
    @Column(nullable = false)
    private int insertions;
    @Column(nullable = false)
    private int deletions;
}
