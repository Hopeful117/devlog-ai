package com.hopeful117.devlogai.documentation.dto.request;

import com.hopeful117.devlogai.documentation.entity.DocumentationType;
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
public class CreateDocumentationRequest {
    @NotNull
    private UUID projectId;


    @NotBlank
    private String title;


    @NotNull
    private DocumentationType type;


    @NotBlank
    private String content;
}
