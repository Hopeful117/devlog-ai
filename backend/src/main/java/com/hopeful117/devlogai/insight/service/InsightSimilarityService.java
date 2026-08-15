package com.hopeful117.devlogai.insight.service;

import org.springframework.stereotype.Service;

import java.text.BreakIterator;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InsightSimilarityService {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "or", "of", "for", "with", "using", "use", "is", "are",
            "was", "were", "be", "been", "being", "have", "has", "had", "do", "does",
            "did", "will", "would", "could", "should", "may", "might", "shall", "can",
            "this", "that", "these", "those", "a", "an", "in", "on", "at", "to", "from",
            "by", "as", "into", "through", "during", "before", "after", "above", "below",
            "between", "out", "off", "over", "under", "again", "further", "then", "once",
            "here", "there", "when", "where", "why", "how", "all", "each", "every",
            "both", "few", "more", "most", "other", "some", "such", "no", "nor", "not",
            "only", "own", "same", "so", "than", "too", "very", "just", "because",
            "but", "if", "while", "about", "against", "project", "application",
            "system", "code", "data", "file", "files", "function", "class", "method",
            "service", "component", "module", "package", "web"
    );

    public Map<String, Double> computeTfIdfVector(String text, List<String> corpus) {
        List<String> terms = tokenize(text);
        if (terms.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> termFreq = new HashMap<>();
        for (String term : terms) {
            termFreq.merge(term, 1, Integer::sum);
        }

        int totalTerms = terms.size();
        int totalDocs = corpus.size();

        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            double tf = (double) entry.getValue() / totalTerms;

            int docsContaining = 0;
            for (String doc : corpus) {
                if (tokenize(doc).contains(term)) {
                    docsContaining++;
                }
            }
            double idf = Math.log(1.0 + (double) totalDocs / (docsContaining + 1));

            vector.put(term, tf * idf);
        }

        return vector;
    }

    public double cosineSimilarity(Map<String, Double> vecA, Map<String, Double> vecB) {
        if (vecA.isEmpty() || vecB.isEmpty()) {
            return 0.0;
        }

        Set<String> allTerms = new HashSet<>(vecA.keySet());
        allTerms.addAll(vecB.keySet());

        double dotProduct = 0.0;
        for (String term : allTerms) {
            double a = vecA.getOrDefault(term, 0.0);
            double b = vecB.getOrDefault(term, 0.0);
            dotProduct += a * b;
        }

        double normA = Math.sqrt(vecA.values().stream().mapToDouble(v -> v * v).sum());
        double normB = Math.sqrt(vecB.values().stream().mapToDouble(v -> v * v).sum());

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (normA * normB);
    }

    public double computeSimilarity(String textA, String textB, List<String> corpus) {
        Map<String, Double> vecA = computeTfIdfVector(textA, corpus);
        Map<String, Double> vecB = computeTfIdfVector(textB, corpus);
        return cosineSimilarity(vecA, vecB);
    }

    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return Arrays.stream(normalized.split(" "))
                .map(this::stem)
                .filter(token -> !token.isBlank())
                .filter(token -> token.length() >= 3)
                .filter(token -> !STOP_WORDS.contains(token))
                .toList();
    }

    private String stem(String token) {
        if (token.endsWith("ies") && token.length() > 4) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.endsWith("ing") && token.length() > 6) {
            String stem = token.substring(0, token.length() - 3);
            if (stem.length() >= 2 && stem.charAt(stem.length() - 1) == stem.charAt(stem.length() - 2)) {
                stem = stem.substring(0, stem.length() - 1);
            }
            return stem;
        }
        if (token.endsWith("tion") && token.length() > 6) {
            return token.substring(0, token.length() - 4);
        }
        if (token.endsWith("ness") && token.length() > 5) {
            return token.substring(0, token.length() - 4);
        }
        if (token.endsWith("ment") && token.length() > 5) {
            return token.substring(0, token.length() - 4);
        }
        if (token.endsWith("able") && token.length() > 5) {
            return token.substring(0, token.length() - 4);
        }
        if (token.endsWith("ible") && token.length() > 5) {
            return token.substring(0, token.length() - 4);
        }
        if (token.endsWith("ly") && token.length() > 4) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("ed") && token.length() > 4) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("er") && token.length() > 4) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("est") && token.length() > 5) {
            return token.substring(0, token.length() - 3);
        }
        if (token.endsWith("s") && !token.endsWith("ss") && token.length() > 4) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }
}
