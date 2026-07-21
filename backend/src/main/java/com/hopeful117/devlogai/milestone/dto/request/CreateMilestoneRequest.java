package com.hopeful117.devlogai.milestone.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMilestoneRequest {
    @NotNull
    private UUID projectId;

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private Instant startedAt;
}
