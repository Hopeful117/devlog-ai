package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.insight.entity.Insight;
import lombok.Value;

@Value
public class PromotionResult {
    Insight promotedInsight;
    SimilarityAssessment similarityAssessment;
}
