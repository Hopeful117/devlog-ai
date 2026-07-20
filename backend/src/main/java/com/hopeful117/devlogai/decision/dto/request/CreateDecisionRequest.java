package com.hopeful117.devlogai.decision.dto.request;

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
public class CreateDecisionRequest {

    @NotNull
    private UUID projectId;


    @NotBlank
    private String title;


    @NotBlank
    private String context;


    @NotBlank
    private String choice;


    @NotBlank
    private String rationale;


    private String consequences;
}