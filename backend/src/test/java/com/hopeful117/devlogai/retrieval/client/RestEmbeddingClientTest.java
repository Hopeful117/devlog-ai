package com.hopeful117.devlogai.retrieval.client;

import com.hopeful117.devlogai.retrieval.client.dto.EmbeddingResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RestEmbeddingClientTest {

    @Test
    void shouldReturnEmbeddingsForValidRequest() throws IOException {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/embeddings", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String responseJson = """
                    {"embeddings":[[0.1,0.2,0.3],[0.4,0.5,0.6]],"model":"text-embedding-3-small","dimensions":3}
                    """;
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            RestEmbeddingClient client = buildClient(server.getAddress().getPort(), Duration.ofSeconds(5));
            Optional<EmbeddingResponse> result = client.embed(List.of("text A", "text B"), "text-embedding-3-small");
            assertTrue(result.isPresent());
            assertEquals(2, result.get().embeddings().size());
            assertEquals(3, result.get().dimensions());
            assertEquals("text-embedding-3-small", result.get().model());
            assertNotNull(receivedBody.get());
            assertTrue(receivedBody.get().contains("\"texts\""));
            assertTrue(receivedBody.get().contains("text-embedding-3-small"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnEmptyOnTimeout() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/embeddings", exchange -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            RestEmbeddingClient client = buildClient(server.getAddress().getPort(), Duration.ofMillis(100));
            Optional<EmbeddingResponse> result = client.embed(List.of("slow text"), "text-embedding-3-small");
            assertTrue(result.isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnEmptyWhenResponseCountMismatch() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/embeddings", exchange -> {
            String responseJson = """
                    {"embeddings":[[0.1]],"model":"text-embedding-3-small","dimensions":1}
                    """;
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            RestEmbeddingClient client = buildClient(server.getAddress().getPort(), Duration.ofSeconds(5));
            // 2 texts but only 1 embedding returned
            Optional<EmbeddingResponse> result = client.embed(List.of("text A", "text B"), "text-embedding-3-small");
            assertTrue(result.isEmpty());
        } finally {
            server.stop(0);
        }
    }

    private RestEmbeddingClient buildClient(int port, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(readTimeout);
        RestClient restClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .requestFactory(factory)
                .build();
        return new RestEmbeddingClient(restClient);
    }
}
