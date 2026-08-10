package com.hopeful117.devlogai.story.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteStoryRequest {

    @NotBlank
    private String targetCommit;
}