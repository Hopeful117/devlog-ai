package com.hopeful117.devlogai.retrieval.client.dto;

import java.util.List;

public record EmbeddingResponse(List<float[]> embeddings, String model, int dimensions) {
}
