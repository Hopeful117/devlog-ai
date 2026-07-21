package com.hopeful117.devlogai.ai.engine.client;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionResponse;
import com.hopeful117.devlogai.ai.engine.exception.AIEngineCommunicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class RestAIEngineClient implements AIEngineClient {

    @Qualifier("aiEngineRestClient")
    private final RestClient restClient;

    @Override
    public AiTaskSubmissionResponse submit(AiTaskSubmissionRequest request) {
        try {
            ResponseEntity<AiTaskSubmissionResponse> response = restClient.post()
                    .uri("/api/v1/ai/tasks")
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
            return body;
        } catch (AIEngineCommunicationException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AIEngineCommunicationException(
                    "AI Engine task submission failed",
                    exception
            );
        }
    }

    private void validateResponse(
            AiTaskSubmissionRequest request,
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
