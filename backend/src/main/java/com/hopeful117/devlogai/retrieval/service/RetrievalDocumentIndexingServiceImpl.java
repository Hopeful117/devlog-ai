package com.hopeful117.devlogai.retrieval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopeful117.devlogai.retrieval.client.EmbeddingClient;
import com.hopeful117.devlogai.retrieval.client.dto.EmbeddingResponse;
import com.hopeful117.devlogai.retrieval.config.EmbeddingClientProperties;
import com.hopeful117.devlogai.retrieval.contract.IndexableDocument;
import com.hopeful117.devlogai.retrieval.repository.RetrievalDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetrievalDocumentIndexingServiceImpl implements RetrievalDocumentIndexingService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RetrievalDocumentRepository repository;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingClientProperties properties;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IndexingResult index(IndexableDocument document) {
        String contentHash = sha256(document.content());

        if (repository.existsByProjectIdAndSourceTypeAndSourceIdAndContentHash(
                document.projectId(), document.sourceType(), document.sourceId(), contentHash)) {
            log.debug("Skipping unchanged document sourceType={} sourceId={}", document.sourceType(), document.sourceId());
            return IndexingResult.SKIPPED;
        }

        Optional<EmbeddingResponse> embeddingResponse = embeddingClient.embed(
                List.of(document.content()),
                properties.defaultModel()
        );

        if (embeddingResponse.isEmpty()) {
            log.warn("Embedding failed for sourceType={} sourceId={}", document.sourceType(), document.sourceId());
            return IndexingResult.EMBEDDING_FAILED;
        }

        EmbeddingResponse embedding = embeddingResponse.get();
        float[] vector = embedding.embeddings().get(0);

        repository.upsertWithEmbedding(
                UUID.randomUUID(),
                document.projectId(),
                document.sourceType(),
                document.sourceId(),
                document.sourceRevision(),
                document.trustTier(),
                document.status(),
                document.title(),
                document.content(),
                contentHash,
                floatArrayToString(vector),
                embedding.model(),
                Instant.now(),
                toJson(document.metadata())
        );

        log.debug("Indexed document sourceType={} sourceId={} model={}",
                document.sourceType(), document.sourceId(), embedding.model());
        return IndexingResult.INDEXED;
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata to JSON, using empty object", e);
            return "{}";
        }
    }

    private static String floatArrayToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
