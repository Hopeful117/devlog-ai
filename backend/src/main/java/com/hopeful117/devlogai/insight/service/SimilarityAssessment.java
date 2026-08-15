package com.hopeful117.devlogai.insight.service;

import lombok.Value;
import java.util.UUID;

@Value
public class SimilarityAssessment {
    boolean hasClosestMatch;
    UUID closestInsightId;
    String closestInsightTitle;
    double similarityScore;
}
