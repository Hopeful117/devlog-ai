package com.hopeful117.devlogai.ai.engine.controller;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultAcknowledgement;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.engine.service.AiTaskResultService;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentCallbackFacade;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/tasks")
public class AiTaskResultController {

    private final AiTaskResultService aiTaskResultService;
    private final AiTaskRepository aiTaskRepository;
    private final StoryContextAgentCallbackFacade storyContextAgentCallbackFacade;

    @Autowired
    public AiTaskResultController(AiTaskResultService aiTaskResultService,
                                  AiTaskRepository aiTaskRepository,
                                  StoryContextAgentCallbackFacade storyContextAgentCallbackFacade) {
        this.aiTaskResultService = aiTaskResultService;
        this.aiTaskRepository = aiTaskRepository;
        this.storyContextAgentCallbackFacade = storyContextAgentCallbackFacade;
    }

    /** Compatibility constructor for isolated generic callback controller tests. */
    public AiTaskResultController(AiTaskResultService aiTaskResultService) {
        this(aiTaskResultService, null, null);
    }

    @PostMapping("/{correlationId}/result")
    public ResponseEntity<AiTaskResultAcknowledgement> receiveResult(
            @PathVariable UUID correlationId,
            @Valid @RequestBody AiTaskResultRequest request
    ) {
        if (aiTaskRepository == null) {
            return ResponseEntity.ok(aiTaskResultService.handle(correlationId, request));
        }
        var task = aiTaskRepository.findByCorrelationId(correlationId)
                .orElseThrow(() -> new EntityNotFoundException("AI task", correlationId));
        if (task.getTaskType() == AiTaskType.STORY_CONTEXT_ANALYSIS) {
            return ResponseEntity.ok(storyContextAgentCallbackFacade.callback(task.getId(), request));
        }
        return ResponseEntity.ok(aiTaskResultService.handle(correlationId, request));
    }
}
