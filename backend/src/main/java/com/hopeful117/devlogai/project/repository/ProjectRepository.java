package com.hopeful117.devlogai.project.repository;

import com.hopeful117.devlogai.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project>findBySlug(String slug);
    boolean existsBySlug(String slug);
}
