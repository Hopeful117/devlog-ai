package com.hopeful117.devlogai.project.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProjectRequest {
    @Pattern(regexp = "(?s).*\\S.*", message = "Project name must not be blank")
    @Size(max = 100)
    private String name;

    @Size(max = 5000)
    private String description;
}
