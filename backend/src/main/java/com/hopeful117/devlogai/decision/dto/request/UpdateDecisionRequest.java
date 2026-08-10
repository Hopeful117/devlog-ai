package com.hopeful117.devlogai.decision.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDecisionRequest {

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
