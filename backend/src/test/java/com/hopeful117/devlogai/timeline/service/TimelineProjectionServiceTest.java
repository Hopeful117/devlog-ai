package com.hopeful117.devlogai.timeline.service;

import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.engineeringevent.EngineeringEvent;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventRepository;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import com.hopeful117.devlogai.knowledge.repository.KnowledgeEventRepository;
import com.hopeful117.devlogai.milestone.entity.Milestone;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;
import com.hopeful117.devlogai.milestone.repository.MilestoneRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import com.hopeful117.devlogai.timeline.dto.TimelineResponse;
import com.hopeful117.devlogai.timeline.dto.TimelineEntryType;
import com.hopeful117.devlogai.timeline.mapper.TimelineMapper;
import com.hopeful117.devlogai.timeline.mapper.TimelineMapperImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimelineProjectionServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private EngineeringStoryRepository storyRepository;
    @Mock
    private EngineeringEventRepository engineeringEventRepository;
    @Mock
    private KnowledgeEventRepository knowledgeEventRepository;
    @Mock
    private DecisionRepository decisionRepository;
    @Mock
    private MilestoneRepository milestoneRepository;
    @Spy
    private TimelineMapper mapper = new TimelineMapperImpl();

    @InjectMocks
    private TimelineProjectionServiceImpl service;

    private Project project(UUID id) {
        Project p = new Project();
        p.setId(id);
        p.setName("Timeline Project");
        return p;
    }

    @Test
    void returnsEmptyEntriesWhenNoData() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project(projectId)));

        TimelineResponse response = service.getTimeline(projectId);

        assertEquals(projectId, response.projectId());
        assertEquals("Timeline Project", response.projectName());
        assertTrue(response.entries().isEmpty());
    }

    @Test
    void sortsByTimestampDescThenTypeNameThenId() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project(projectId)));

        Instant common = Instant.parse("2026-08-01T10:00:00Z");
        Instant earlier = Instant.parse("2026-07-01T10:00:00Z");

        Decision decision = Decision.builder()
                .id(UUID.randomUUID())
                .title("A decision")
                .createdAt(common)
                .build();
        KnowledgeEvent knowledge = KnowledgeEvent.builder()
                .id(UUID.randomUUID())
                .title("A lesson")
                .type(com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType.ARCHITECTURE)
                .createdAt(common)
                .build();
        EngineeringStory story = EngineeringStory.builder()
                .id(UUID.randomUUID())
                .storyNumber(3)
                .title("Old story")
                .completedAt(earlier)
                .build();

        when(decisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc(eq(projectId), any()))
                .thenReturn(List.of(decision));
        when(knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(eq(projectId), any()))
                .thenReturn(List.of(knowledge));
        when(storyRepository.findByProject_IdAndStatusOrderByCompletedAtDescIdDesc(
                eq(projectId), eq(StoryStatus.COMPLETED), any()))
                .thenReturn(List.of(story));

        var entries = service.getTimeline(projectId).entries();

        assertEquals(3, entries.size());
        assertEquals(TimelineEntryType.DECISION, entries.get(0).type());
        assertEquals(TimelineEntryType.KNOWLEDGE_EVENT, entries.get(1).type());
        assertEquals(TimelineEntryType.STORY_COMPLETED, entries.get(2).type());
    }

    @Test
    void boundsGlobalResultToTwenty() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project(projectId)));

        List<KnowledgeEvent> many = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            many.add(KnowledgeEvent.builder()
                    .id(UUID.randomUUID())
                    .title("Event " + i)
                    .type(com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType.FEATURE)
                    .createdAt(Instant.ofEpochMilli(10_000_000 + i))
                    .build());
        }
        when(knowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc(eq(projectId), any()))
                .thenReturn(many);

        var entries = service.getTimeline(projectId).entries();

        assertEquals(20, entries.size());
        assertTrue(entries.get(0).timestamp().isAfter(entries.get(19).timestamp()));
    }

    @Test
    void includesCompletedMilestoneAndEngineeringEvent() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project(projectId)));

        Instant now = Instant.now();
        Milestone milestone = Milestone.builder()
                .id(UUID.randomUUID())
                .name("MVP")
                .completedAt(now)
                .build();
        EngineeringEvent event = EngineeringEvent.builder()
                .id(UUID.randomUUID())
                .title("Shipped timeline")
                .category(com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory.FEATURE_INTRODUCTION)
                .occurredAt(now.minusSeconds(10))
                .build();

        when(milestoneRepository.findByProjectIdAndStatusOrderByCompletedAtDescIdDesc(
                eq(projectId), eq(MilestoneStatus.COMPLETED), any()))
                .thenReturn(List.of(milestone));
        when(engineeringEventRepository.findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(
                eq(projectId), any()))
                .thenReturn(List.of(event));

        var entries = service.getTimeline(projectId).entries();

        assertEquals(2, entries.size());
        assertEquals(TimelineEntryType.MILESTONE_COMPLETED, entries.get(0).type());
        assertEquals(TimelineEntryType.ENGINEERING_EVENT, entries.get(1).type());
    }

    @Test
    void filtersCompletedMilestoneWithoutCompletedAt() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project(projectId)));

        Milestone incomplete = Milestone.builder()
                .id(UUID.randomUUID())
                .name("no date")
                .build();
        when(milestoneRepository.findByProjectIdAndStatusOrderByCompletedAtDescIdDesc(
                eq(projectId), eq(MilestoneStatus.COMPLETED), any()))
                .thenReturn(List.of(incomplete));

        assertTrue(service.getTimeline(projectId).entries().isEmpty());
    }

    @Test
    void throwsWhenProjectNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.getTimeline(projectId));
    }
}