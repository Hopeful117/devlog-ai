package com.hopeful117.devlogai.retrieval.repository;

import com.hopeful117.devlogai.retrieval.entity.RetrievalIndexingQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RetrievalIndexingQueueRepository extends JpaRepository<RetrievalIndexingQueueEntry, UUID> {

    List<RetrievalIndexingQueueEntry> findTop10ByStatusOrderByQueuedAtAsc(String status);
}
