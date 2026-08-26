package com.hopeful117.devlogai.source.repository;

import com.hopeful117.devlogai.source.entity.Source;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceRepository extends JpaRepository<Source, UUID> {

    List<Source> findByProjectIdOrderByCreatedAtDescIdDesc(UUID projectId);

    List<Source> findByProjectIdAndActiveTrueOrderByCreatedAtDescIdDesc(UUID projectId);

    List<Source> findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(UUID projectId);

    Optional<Source> findByIdAndProject_IdAndActiveTrue(UUID id, UUID projectId);

    @EntityGraph(attributePaths = {"project"})
    List<Source> findByTypeAndActiveTrueOrderByProjectIdAscCreatedAtAscIdAsc(
            com.hopeful117.devlogai.source.entity.SourceType type);

    @EntityGraph(attributePaths = {"project"})
    Optional<Source> findWithProjectById(UUID id);
}
