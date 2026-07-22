package com.hopeful117.devlogai.ai.engine.client;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionRequest;
import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionResponse;

public interface AIEngineClient {

    AiTaskSubmissionResponse submit(PromptRequest request);

    @Deprecated
    AiTaskSubmissionResponse submit(AiTaskSubmissionRequest request);
}
