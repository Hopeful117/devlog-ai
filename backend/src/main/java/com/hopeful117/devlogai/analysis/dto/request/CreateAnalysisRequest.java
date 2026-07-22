package com.hopeful117.devlogai.analysis.dto.request;

import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateAnalysisRequest {
    @NotNull
    private UUID projectId;

    @NotNull
    private AnalysisType type;

    @Size(max = 255)
    private String targetRevision;
}
