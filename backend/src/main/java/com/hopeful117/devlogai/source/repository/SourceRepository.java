package com.hopeful117.devlogai.source.repository;

import com.hopeful117.devlogai.source.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SourceRepository extends JpaRepository<Source, UUID> {

    List<Source> findByProjectIdOrderByCreatedAtDescIdDesc(UUID projectId);

    List<Source> findByProjectIdAndActiveTrueOrderByCreatedAtDescIdDesc(UUID projectId);
}
