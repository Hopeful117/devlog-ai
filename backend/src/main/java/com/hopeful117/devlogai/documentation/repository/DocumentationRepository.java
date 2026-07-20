package com.hopeful117.devlogai.documentation.repository;

import com.hopeful117.devlogai.documentation.entity.Documentation;
import com.hopeful117.devlogai.documentation.entity.DocumentationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentationRepository extends JpaRepository<Documentation, UUID> {
    List<Documentation> findByProjectIdOrderByVersionDesc(UUID uuid);

    List<Documentation >findByProjectIdAndTypeOrderByVersionDesc(UUID uuid, DocumentationType documentationType);
}
