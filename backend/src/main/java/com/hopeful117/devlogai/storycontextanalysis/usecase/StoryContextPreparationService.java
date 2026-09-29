package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.EngineeringContextFacade;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/** Owns Core-authoritative project, story, context and projection preparation. */
@Service
@RequiredArgsConstructor
public class StoryContextPreparationService {
    private final ProjectRepository projectRepository;
    private final EngineeringStoryRepository storyRepository;
    private final EngineeringContextFacade engineeringContextFacade;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private StoryContextAgentMetrics metrics;

    public PreparedStoryContext prepare(String projectSlug, UUID storyId, String intent, List<String> files) {
        Project project = projectRepository.findBySlug(projectSlug)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectSlug));
        EngineeringStory story = storyId == null ? null : storyRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("EngineeringStory", storyId));
        if (story != null && !story.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("Story does not belong to project");
        }

        List<String> requestedFiles = files == null ? List.of() : List.copyOf(files);
        CanonicalEngineeringContext canonical = engineeringContextFacade.getCanonicalEngineeringContext(
                projectSlug, intent, requestedFiles, storyId);
        if (canonical == null) {
            throw new IllegalStateException("Canonical EngineeringContext is required for Story Context Analysis");
        }

        var projection = StoryContextAgentProjectionV1.build(
                canonical, projectSlug, storyId, intent, requestedFiles, story, objectMapper);
        if (metrics != null) {
            metrics.increment("sca_projection_construction_total");
            if (Boolean.TRUE.equals(((java.util.Map<?, ?>) projection.get("accounting")).get("truncated"))) {
                metrics.increment("sca_budget_truncated_total");
            }
        }

        return new PreparedStoryContext(
                projectSlug, storyId, intent, requestedFiles, project, story, canonical, projection,
                (String) projection.get("projectionDigest"));
    }
}
