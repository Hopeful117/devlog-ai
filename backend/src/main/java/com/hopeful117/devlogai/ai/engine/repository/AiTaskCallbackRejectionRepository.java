package com.hopeful117.devlogai.ai.engine.repository;

import com.hopeful117.devlogai.ai.engine.entity.AiTaskCallbackRejection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiTaskCallbackRejectionRepository extends JpaRepository<AiTaskCallbackRejection, UUID> {
}
