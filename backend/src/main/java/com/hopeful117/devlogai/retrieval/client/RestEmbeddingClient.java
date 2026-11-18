package com.hopeful117.devlogai.retrieval.client;

import com.hopeful117.devlogai.retrieval.client.dto.EmbeddingRequest;
import com.hopeful117.devlogai.retrieval.client.dto.EmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestEmbeddingClient implements EmbeddingClient {

    @Qualifier("embeddingRestClient")
    private final RestClient restClient;

    @Override
    public Optional<EmbeddingResponse> embed(List<String> texts, String model) {
        long startedAt = System.nanoTime();
        try {
            EmbeddingResponse response = restClient.post()
                    .uri("/api/v1/embeddings")
                    .body(new EmbeddingRequest(texts, model))
                    .retrieve()
                    .body(EmbeddingResponse.class);
            if (response == null || response.embeddings() == null || response.embeddings().size() != texts.size()) {
                log.warn("Embedding response invalid: textCount={} responseSize={}",
                        texts.size(),
                        response == null ? "null" : (response.embeddings() == null ? "null-list" : response.embeddings().size()));
                return Optional.empty();
            }
            log.debug("Embedding call succeeded textCount={} model={} durationMs={}",
                    texts.size(), model, elapsedMs(startedAt));
            return Optional.of(response);
        } catch (ResourceAccessException exception) {
            log.warn("Embedding call timed out or connection refused textCount={} durationMs={} message={}",
                    texts.size(), elapsedMs(startedAt), exception.getMessage());
            return Optional.empty();
        } catch (RestClientException exception) {
            log.warn("Embedding call failed textCount={} durationMs={} exceptionType={}",
                    texts.size(), elapsedMs(startedAt), exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
