package com.hopeful117.devlogai.insight.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InsightSimilarityServiceTest {

    private final InsightSimilarityService service = new InsightSimilarityService();

    @Test
    void shouldTokenizeTextIntoNormalizedTerms() {
        List<String> tokens = service.tokenize("Spring Boot REST API Application");
        assertFalse(tokens.isEmpty());
        assertTrue(tokens.contains("spring"), "tokens: " + tokens);
        assertTrue(tokens.contains("boot"), "tokens: " + tokens);
        assertTrue(tokens.contains("rest"), "tokens: " + tokens);
        assertTrue(tokens.contains("api"), "tokens: " + tokens);
    }

    @Test
    void shouldFilterStopWords() {
        List<String> tokens = service.tokenize("The application is using Spring Boot");
        assertFalse(tokens.contains("the"), "tokens: " + tokens);
        assertFalse(tokens.contains("is"), "tokens: " + tokens);
        assertFalse(tokens.contains("using"), "tokens: " + tokens);
        assertTrue(tokens.contains("spring"), "tokens: " + tokens);
        assertTrue(tokens.contains("boot"), "tokens: " + tokens);
    }

    @Test
    void shouldStemTokens() {
        List<String> tokens = service.tokenize("testing running quickly");
        assertTrue(tokens.contains("test"), "tokens: " + tokens);
        assertTrue(tokens.contains("run"), "tokens: " + tokens);
        assertTrue(tokens.contains("quick"), "tokens: " + tokens);
    }

    @Test
    void shouldReturnIdenticalVectorForIdenticalText() {
        List<String> corpus = List.of(
                "Spring Boot REST API",
                "Docker container deployment",
                "Automated testing infrastructure"
        );
        Map<String, Double> vecA = service.computeTfIdfVector("Spring Boot REST API", corpus);
        Map<String, Double> vecB = service.computeTfIdfVector("Spring Boot REST API", corpus);
        assertEquals(1.0, service.cosineSimilarity(vecA, vecB), 0.001);
    }

    @Test
    void shouldReturnZeroForCompletelyDifferentTexts() {
        List<String> corpus = List.of(
                "Spring Boot REST API",
                "Docker container deployment"
        );
        Map<String, Double> vecA = service.computeTfIdfVector("Spring Boot REST API", corpus);
        Map<String, Double> vecB = service.computeTfIdfVector("Docker container deployment", corpus);
        double similarity = service.cosineSimilarity(vecA, vecB);
        assertTrue(similarity < 0.3, "Similarity should be low for different texts: " + similarity);
    }

    @Test
    void shouldDetectSimilarContentWithDifferentTitles() {
        List<String> corpus = List.of(
                "Containerized Deployment Using Docker and Docker Compose",
                "Use of Docker and Docker Compose",
                "Spring Boot REST API development"
        );
        double similarity = service.computeSimilarity(
                "Containerized Deployment Using Docker and Docker Compose",
                "Use of Docker and Docker Compose",
                corpus
        );
        assertTrue(similarity >= 0.5, "Should detect similar content: " + similarity);
    }

    @Test
    void shouldComputeTfIdfVector() {
        List<String> corpus = List.of(
                "Spring Boot REST API",
                "Docker container deployment",
                "Spring framework application"
        );
        Map<String, Double> vector = service.computeTfIdfVector("Spring Boot REST API", corpus);
        assertFalse(vector.isEmpty(), "vector: " + vector);
        assertTrue(vector.containsKey("spring"), "vector: " + vector);
        assertTrue(vector.get("spring") > 0);
    }

    @Test
    void shouldReturnEmptyVectorForEmptyText() {
        List<String> corpus = List.of("Spring Boot REST API");
        Map<String, Double> vector = service.computeTfIdfVector("", corpus);
        assertTrue(vector.isEmpty());
    }

    @Test
    void shouldReturnEmptyVectorForNullText() {
        List<String> corpus = List.of("Spring Boot REST API");
        Map<String, Double> vector = service.computeTfIdfVector(null, corpus);
        assertTrue(vector.isEmpty());
    }

    @Test
    void shouldReturnZeroSimilarityForEmptyVectors() {
        assertEquals(0.0, service.cosineSimilarity(Map.of(), Map.of()));
        assertEquals(0.0, service.cosineSimilarity(Map.of("a", 1.0), Map.of()));
        assertEquals(0.0, service.cosineSimilarity(Map.of(), Map.of("b", 1.0)));
    }
}
