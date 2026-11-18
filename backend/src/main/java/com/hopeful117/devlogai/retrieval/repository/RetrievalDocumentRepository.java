package com.hopeful117.devlogai.retrieval.repository;

import com.hopeful117.devlogai.retrieval.entity.RetrievalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetrievalDocumentRepository extends JpaRepository<RetrievalDocument, UUID> {

    Optional<RetrievalDocument> findByProjectIdAndSourceTypeAndSourceId(
            UUID projectId, String sourceType, UUID sourceId);

    void deleteByProjectIdAndSourceTypeAndSourceId(
            UUID projectId, String sourceType, UUID sourceId);

    boolean existsByProjectIdAndSourceTypeAndSourceIdAndContentHash(
            UUID projectId, String sourceType, UUID sourceId, String contentHash);
}
