package com.hopeful117.devlogai.ai.engine.client;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionRequest;
import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionResponse;
import com.hopeful117.devlogai.ai.engine.dto.DeliverableGenerationRequest;
import com.hopeful117.devlogai.ai.engine.dto.DeliverableGenerationResponse;

public interface AIEngineClient {

    AiTaskSubmissionResponse submit(PromptRequest request);

    DeliverableGenerationResponse generateDeliverable(DeliverableGenerationRequest request);

    @Deprecated
    AiTaskSubmissionResponse submit(AiTaskSubmissionRequest request);
}
