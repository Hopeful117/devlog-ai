package com.hopeful117.devlogai.projectstate.mapper;

import com.hopeful117.devlogai.engineeringevent.EngineeringEvent;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType;
import com.hopeful117.devlogai.projectstate.dto.inner.EvolutionSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.HumanContextInputSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.KnowledgeSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.ProposalSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.StorySummary;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInput;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputStatus;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectStateMapperTest {

    private final ProjectStateMapper mapper = new ProjectStateMapperImpl();

    @Test
    void mapsStoryNumberToNumber() {
        EngineeringStory story = EngineeringStory.builder()
                .storyNumber(42)
                .title("Overview fix")
                .status(StoryStatus.IN_PROGRESS)
                .build();

        StorySummary summary = mapper.toStorySummary(story);

        assertEquals(42, summary.number());
        assertEquals("Overview fix", summary.title());
        assertEquals(StoryStatus.IN_PROGRESS, summary.status());
    }

    @Test
    void mapsEmptyStoryListToEmptySummaryList() {
        assertEquals(0, mapper.toStorySummaries(java.util.List.of()).size());
    }

    @Test
    void mapsKnowledgeEventToKnowledgeSummary() {
        Instant createdAt = Instant.now();
        KnowledgeEvent event = KnowledgeEvent.builder()
                .id(UUID.randomUUID())
                .type(KnowledgeEventType.ARCHITECTURE)
                .title("Adopted hexagonal layout")
                .createdAt(createdAt)
                .build();

        KnowledgeSummary summary = mapper.toKnowledgeSummary(event);

        assertEquals(event.getId(), summary.id());
        assertEquals(KnowledgeEventType.ARCHITECTURE, summary.type());
        assertEquals("Adopted hexagonal layout", summary.title());
        assertEquals(createdAt, summary.createdAt());
    }

    @Test
    void mapsEmptyKnowledgeEventsToEmptyList() {
        assertEquals(0, mapper.toKnowledgeSummaries(java.util.List.of()).size());
        assertEquals(0, mapper.toRecentKnowledgeSection(java.util.List.of()).recentKnowledge().size());
    }

    @Test
    void mapsEngineeringEventToEvolutionSummary() {
        Instant occurredAt = Instant.now();
        EngineeringEvent event = EngineeringEvent.builder()
                .id(UUID.randomUUID())
                .category(EngineeringEventCategory.BUG_RESOLUTION)
                .title("Fixed N+1 in projection")
                .baseCommit("92d3f1e")
                .targetCommit("7ac09b2")
                .occurredAt(occurredAt)
                .build();

        EvolutionSummary summary = mapper.toEvolutionSummary(event);

        assertEquals(event.getId(), summary.id());
        assertEquals(EngineeringEventCategory.BUG_RESOLUTION, summary.category());
        assertEquals("Fixed N+1 in projection", summary.title());
        assertEquals("92d3f1e", summary.baseCommit());
        assertEquals("7ac09b2", summary.targetCommit());
        assertEquals(occurredAt, summary.occurredAt());
    }

    @Test
    void mapsEmptyEngineeringEventsToEmptyList() {
        assertEquals(0, mapper.toEvolutionSummaries(java.util.List.of()).size());
        assertEquals(0, mapper.toRecentEvolutionSection(java.util.List.of()).recentEvolution().size());
    }

    @Test
    void mapsInsightProposalPayloadIntoProposalSummary() {
        UUID id = UUID.randomUUID();
        ValidatableProposal proposal = ValidatableProposal.builder()
                .id(id)
                .type(ProposalType.INSIGHT)
                .status(ProposalStatus.PROPOSED)
                .confidence(new BigDecimal("0.9500"))
                .payload(Map.of(
                        "insightType", "PROJECT_PRESENTATION",
                        "title", "Project Overview of devlog-ai",
                        "summary", "devlog-ai is an AI-powered documentation assistant."
                ))
                .build();

        ProposalSummary summary = mapper.toProposalSummary(proposal);

        assertEquals(id, summary.id());
        assertEquals("INSIGHT", summary.type());
        assertEquals("PROJECT_PRESENTATION", summary.insightType());
        assertEquals("Project Overview of devlog-ai", summary.title());
        assertEquals("devlog-ai is an AI-powered documentation assistant.", summary.summary());
        assertEquals(new BigDecimal("0.9500"), summary.confidence());
    }

    @Test
    void mapsHumanContextInputToSummary() {
        Instant updatedAt = Instant.now();
        ProjectHumanContextInput input = ProjectHumanContextInput.builder()
                .id(UUID.randomUUID())
                .title("Medium-term objective")
                .contentMarkdown("Improve context quality for humans and agents.")
                .type(ProjectHumanContextInputType.GOAL)
                .status(ProjectHumanContextInputStatus.ACTIVE)
                .updatedAt(updatedAt)
                .build();

        HumanContextInputSummary summary = mapper.toHumanContextInputSummary(input);

        assertEquals(input.getId(), summary.id());
        assertEquals(ProjectHumanContextInputType.GOAL, summary.type());
        assertEquals("Medium-term objective", summary.title());
        assertEquals(updatedAt, summary.updatedAt());
    }

    @Test
    void mapsEmptyHumanContextInputListToEmptySummaryList() {
        assertTrue(mapper.toHumanContextInputSummaries(java.util.List.of()).isEmpty());
    }
}
