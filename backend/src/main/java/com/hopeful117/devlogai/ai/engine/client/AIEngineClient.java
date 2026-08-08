package com.hopeful117.devlogai.ai.engine.client;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionRequest;
import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionResponse;
import com.hopeful117.devlogai.ai.engine.dto.DeliverableGenerationRequest;
import com.hopeful117.devlogai.ai.engine.dto.DeliverableGenerationResponse;

public interface AIEngineClient {

    AiTaskSubmissionResponse submit(PromptRequest request);

    DeliverableGenerationResponse generateDeliverable(DeliverableGenerationRequest request);

    /**
     * @deprecated The legacy request does not carry selected repository knowledge.
     * Use {@link #submit(PromptRequest)} instead.
     */
    @Deprecated(since = "0.3.0", forRemoval = false)
    AiTaskSubmissionResponse submit(AiTaskSubmissionRequest request);
}
