package com.hopeful117.devlogai.knowledge.dto.request;

import com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType;
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
public class CreateKnowledgeEventRequest {
    @NotNull
    private UUID projectId;


    @NotNull
    private KnowledgeEventType type;


    @NotBlank
    private String title;


    private String description;
}
