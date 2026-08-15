package com.hopeful117.devlogai.insight.service;

import java.util.List;

public class DebugTokenizer {
    public static void main(String[] args) {
        InsightSimilarityService service = new InsightSimilarityService();
        System.out.println("A tokens: " + service.tokenize("Containerized Deployment Using Docker and Docker Compose"));
        System.out.println("B tokens: " + service.tokenize("Use of Docker and Docker Compose"));
        System.out.println("C tokens: " + service.tokenize("Automated and Integration Testing Infrastructure"));
        System.out.println("D tokens: " + service.tokenize("The project includes automated tests as well as integration tests"));
        System.out.println("E tokens: " + service.tokenize("Spring Boot REST API"));
        System.out.println("F tokens: " + service.tokenize("Spring framework application"));

        List<String> corpus = List.of(
            "Containerized Deployment Using Docker and Docker Compose",
            "Use of Docker and Docker Compose",
            "Automated and Integration Testing Infrastructure",
            "The project includes automated tests as well as integration tests",
            "Spring Boot REST API",
            "Spring framework application"
        );
        System.out.println("Similarity A-B: " + service.computeSimilarity(
            "Containerized Deployment Using Docker and Docker Compose",
            "Use of Docker and Docker Compose", corpus));
        System.out.println("Similarity C-D: " + service.computeSimilarity(
            "Automated and Integration Testing Infrastructure",
            "The project includes automated tests as well as integration tests", corpus));
        System.out.println("Similarity E-F: " + service.computeSimilarity(
            "Spring Boot REST API",
            "Spring framework application", corpus));
    }
}
