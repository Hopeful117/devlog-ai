package com.hopeful117.devlogai.challenge.dto.request;

import com.hopeful117.devlogai.challenge.entity.ChallengeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChallengeRequest {

    @NotNull
    private UUID projectId;

    @NotBlank
    private String title;

    private String description;

    private String impact;

    private ChallengeStatus status;

    private String resolution;
}
