package com.hopeful117.devlogai.fact.dto.request;

import com.hopeful117.devlogai.fact.entity.FactType;
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
public class CreateFactRequest {

    @NotNull
    private UUID analysisId;

    @NotNull
    private FactType type;

    @NotBlank
    private String content;

    @NotBlank
    private String source;

    @NotEmpty
    private Set<@NotBlank String> evidenceReferences;
}
