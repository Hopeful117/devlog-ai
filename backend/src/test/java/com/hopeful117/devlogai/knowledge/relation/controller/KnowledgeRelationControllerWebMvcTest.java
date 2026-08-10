package com.hopeful117.devlogai.knowledge.relation.controller;

import com.hopeful117.devlogai.knowledge.relation.dto.response.KnowledgeRelationResponse;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;
import com.hopeful117.devlogai.knowledge.relation.service.KnowledgeRelationService;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KnowledgeRelationControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldExposeAllKnowledgeRelationRoutes() throws Exception {
        KnowledgeRelationService service =
                mock(KnowledgeRelationService.class);
        MockMvc mvc = mockMvc(
                new KnowledgeRelationController(service));

        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        KnowledgeRelationResponse response =
                new KnowledgeRelationResponse(
                        id,
                        projectId,
                        EntityType.CHALLENGE,
                        sourceId,
                        EntityType.DECISION,
                        targetId,
                        KnowledgeRelationType.RESOLVES,
                        "Resolved the challenge",
                        null
                );

        when(service.create(any())).thenReturn(response);
        when(service.getById(id)).thenReturn(response);
        when(service.getByProject(projectId))
                .thenReturn(List.of(response));
        when(service.getBySource(EntityType.CHALLENGE, sourceId))
                .thenReturn(List.of(response));
        when(service.getByTarget(EntityType.DECISION, targetId))
                .thenReturn(List.of(response));

        mvc.perform(post("/api/v1/knowledge-relations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "projectId":"%s",
                                    "sourceEntityType":"CHALLENGE",
                                    "sourceEntityId":"%s",
                                    "targetEntityType":"DECISION",
                                    "targetEntityId":"%s",
                                    "relationType":"RESOLVES",
                                    "description":"Resolved the challenge"
                                }
                                """.formatted(
                                projectId, sourceId, targetId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/knowledge-relations/" + id));

        mvc.perform(get("/api/v1/knowledge-relations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationType")
                        .value("RESOLVES"));

        mvc.perform(get(
                "/api/v1/knowledge-relations/project/{projectId}",
                projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId")
                        .value(projectId.toString()));

        mvc.perform(get(
                "/api/v1/knowledge-relations/source/{entityType}/{entityId}",
                "CHALLENGE", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceEntityType")
                        .value("CHALLENGE"));

        mvc.perform(get(
                "/api/v1/knowledge-relations/target/{entityType}/{entityId}",
                "DECISION", targetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetEntityType")
                        .value("DECISION"));

        mvc.perform(delete(
                "/api/v1/knowledge-relations/{id}", id))
                .andExpect(status().isNoContent());

        verify(service).delete(id);
    }
}
