package com.hopeful117.devlogai.insight.dto.request;

import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateInsightRequest {
    @NotNull
    private UUID projectId;

    @NotNull
    private UUID analysisId;

    @NotNull
    private InsightType type;

    @NotNull
    private InsightSeverity severity;

    @NotBlank
    private String title;

    @NotBlank
    private String content;
}
