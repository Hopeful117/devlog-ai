package com.hopeful117.devlogai.projectcontextinput.dto.request;

import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProjectHumanContextInputRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 20000) String contentMarkdown,
        @NotNull ProjectHumanContextInputType type
) {
}
