package com.hopeful117.devlogai.project.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProjectRequest {
    @Size(max = 100)
    private String name;

    @Size(max = 5000)
    private String description;
}
