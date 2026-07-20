package com.hopeful117.devlogai.project.dto.response;

import com.hopeful117.devlogai.project.entity.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectResponse {
    private UUID id;

    private String name;

    private String slug;

    private String description;

    private ProjectStatus status;

    private Instant createdAt;

    private Instant updatedAt;
}
