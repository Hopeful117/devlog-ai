package com.hopeful117.devlogai.ai.engine.client;

import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionResponse;
import com.hopeful117.devlogai.ai.engine.exception.AIEngineCommunicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static com.hopeful117.devlogai.shared.logging.CorrelationIdFilter.HEADER_NAME;
import static com.hopeful117.devlogai.shared.logging.CorrelationIdFilter.MDC_KEY;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestAIEngineClient implements AIEngineClient {

    @Qualifier("aiEngineRestClient")
    private final RestClient restClient;

    @Override
    public AiTaskSubmissionResponse submit(PromptRequest request) {
        long startedAt = System.nanoTime();
        log.info("Submitting AI task correlationId={} analysisId={} taskType={}",
                request.correlationId(), request.analysisId(), request.taskType());
        try {
            ResponseEntity<AiTaskSubmissionResponse> response = restClient.post()
                    .uri("/api/v1/ai/tasks")
                    .header(HEADER_NAME, outboundCorrelationId(request))
                    .body(request)
                    .retrieve()
                    .toEntity(AiTaskSubmissionResponse.class);

            if (response.getStatusCode() != HttpStatus.ACCEPTED) {
                throw new AIEngineCommunicationException(
                        "AI Engine returned unexpected status %s"
                                .formatted(response.getStatusCode())
                );
            }

            AiTaskSubmissionResponse body = response.getBody();
            validateResponse(request, body);
            log.info("AI task accepted correlationId={} durationMs={}",
                    request.correlationId(), elapsedMs(startedAt));
            return body;
        } catch (AIEngineCommunicationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("AI task submission failed correlationId={} durationMs={} exceptionType={}",
                    request.correlationId(), elapsedMs(startedAt),
                    exception.getClass().getSimpleName());
            throw new AIEngineCommunicationException(
                    "AI Engine task submission failed",
                    exception
            );
        }
    }

    @Override
    @Deprecated
    public AiTaskSubmissionResponse submit(AiTaskSubmissionRequest request) {
        throw new AIEngineCommunicationException(
                "Legacy AnalysisContext submission is disabled; submit a PromptRequest with SelectedKnowledge"
        );
    }

    private String outboundCorrelationId(PromptRequest request) {
        String requestCorrelationId = MDC.get(MDC_KEY);
        return requestCorrelationId == null ? request.correlationId().toString() : requestCorrelationId;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private void validateResponse(
            PromptRequest request,
            AiTaskSubmissionResponse response
    ) {
        if (response == null) {
            throw new AIEngineCommunicationException(
                    "AI Engine returned an empty submission response"
            );
        }
        if (!request.correlationId().equals(response.correlationId())) {
            throw new AIEngineCommunicationException(
                    "AI Engine returned a different correlation identifier"
            );
        }
        if (!response.accepted()) {
            throw new AIEngineCommunicationException(
                    "AI Engine rejected the task submission"
            );
        }
        if (response.acceptedAt() == null) {
            throw new AIEngineCommunicationException(
                    "AI Engine response does not contain an acceptance timestamp"
            );
        }
    }
}
