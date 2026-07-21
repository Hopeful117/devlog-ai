package com.hopeful117.devlogai.ai.engine.service;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultAcknowledgement;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;

import java.util.UUID;

public interface AiTaskResultService {

    AiTaskResultAcknowledgement handle(
            UUID correlationId,
            AiTaskResultRequest request
    );
}
