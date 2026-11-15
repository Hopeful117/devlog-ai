package com.hopeful117.devlogai.mcptoolobservability;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface McpToolEventRepository extends JpaRepository<McpToolEvent, UUID> {
    Page<McpToolEvent> findByProjectIdOrderByReceivedAtDesc(UUID projectId, Pageable pageable);
    boolean existsByInstanceIdAndOperationIdAndEventType(String instanceId, String operationId, String eventType);
}
