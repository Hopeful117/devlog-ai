package com.hopeful117.devlogai.story.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEngineeringStoryRequest {

    @NotNull
    private UUID projectId;

    @NotBlank
    private String title;

    @NotNull
    private Integer storyNumber;

    @NotBlank
    private String storyPath;
}