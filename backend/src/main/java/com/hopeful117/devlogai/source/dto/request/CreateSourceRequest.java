package com.hopeful117.devlogai.source.dto.request;

import com.hopeful117.devlogai.source.entity.GitProvider;
import com.hopeful117.devlogai.source.entity.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSourceRequest(
        @NotNull UUID projectId,
        @NotNull SourceType type,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 2000) String repositoryUrl,
        @Size(max = 255) String defaultBranch,
        GitProvider provider
) {
}
