package com.hopeful117.devlogai.observation.dto.request;

import com.hopeful117.devlogai.observation.entity.ObservationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateObservationRequest {

    @NotNull
    private UUID analysisId;

    @NotNull
    private ObservationType type;

    @NotBlank
    private String content;

    @NotEmpty
    private Set<UUID> supportingFactIds;
}
