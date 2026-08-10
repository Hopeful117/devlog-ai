package com.hopeful117.devlogai.knowledge.relation.dto.request;

import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateKnowledgeRelationRequest {

    @NotNull
    private UUID projectId;

    @NotNull
    private EntityType sourceEntityType;

    @NotNull
    private UUID sourceEntityId;

    @NotNull
    private EntityType targetEntityType;

    @NotNull
    private UUID targetEntityId;

    @NotNull
    private KnowledgeRelationType relationType;

    private String description;
}
