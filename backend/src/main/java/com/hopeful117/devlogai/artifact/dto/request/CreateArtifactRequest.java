package com.hopeful117.devlogai.artifact.dto.request;

import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateArtifactRequest {

    @NotNull
    private UUID projectId;


    @NotBlank
    private String name;


    @NotNull
    private ArtifactType type;


    private String path;


    private String description;
}
