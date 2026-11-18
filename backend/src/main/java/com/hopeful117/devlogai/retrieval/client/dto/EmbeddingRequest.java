package com.hopeful117.devlogai.retrieval.client.dto;

import java.util.List;

public record EmbeddingRequest(List<String> texts, String model) {
}
