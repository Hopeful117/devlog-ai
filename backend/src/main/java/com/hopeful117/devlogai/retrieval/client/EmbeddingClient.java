package com.hopeful117.devlogai.retrieval.client;

import com.hopeful117.devlogai.retrieval.client.dto.EmbeddingResponse;

import java.util.List;
import java.util.Optional;

public interface EmbeddingClient {

    /**
     * Requests embeddings for a batch of plain-text inputs.
     * Returns {@link Optional#empty()} on timeout or connection failure;
     * the caller must decide whether to skip or retry the document.
     */
    Optional<EmbeddingResponse> embed(List<String> texts, String model);
}
