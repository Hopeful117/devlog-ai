package com.hopeful117.devlogai.timeline.mapper;

import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.engineeringevent.EngineeringEvent;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType;
import com.hopeful117.devlogai.milestone.entity.Milestone;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import com.hopeful117.devlogai.timeline.dto.TimelineEntryType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimelineMapperTest {

    private final TimelineMapper mapper = new TimelineMapperImpl();

    @Test
    void mapsCompletedStoryToStoryEntry() {
        Instant completedAt = Instant.now();
        EngineeringStory story = EngineeringStory.builder()
                .id(UUID.randomUUID())
                .storyNumber(7)
                .title("Build timeline")
                .status(StoryStatus.COMPLETED)
                .completedAt(completedAt)
                .build();

        var entry = mapper.toStoryEntry(story);

        assertEquals(story.getId(), entry.id());
        assertEquals(TimelineEntryType.STORY_COMPLETED, entry.type());
        assertEquals(completedAt, entry.timestamp());
        assertEquals("Build timeline", entry.title());
        assertEquals("#7", entry.detail());
    }

    @Test
    void mapsEngineeringEventToEntry() {
        Instant occurredAt = Instant.now();
        EngineeringEvent event = EngineeringEvent.builder()
                .id(UUID.randomUUID())
                .category(EngineeringEventCategory.BUG_RESOLUTION)
                .title("Fixed ordering")
                .occurredAt(occurredAt)
                .build();

        var entry = mapper.toEngineeringEventEntry(event);

        assertEquals(TimelineEntryType.ENGINEERING_EVENT, entry.type());
        assertEquals(occurredAt, entry.timestamp());
        assertEquals("Fixed ordering", entry.title());
        assertEquals("BUG_RESOLUTION", entry.detail());
    }

    @Test
    void mapsKnowledgeEventToEntry() {
        Instant createdAt = Instant.now();
        KnowledgeEvent event = KnowledgeEvent.builder()
                .id(UUID.randomUUID())
                .type(KnowledgeEventType.ARCHITECTURE)
                .title("Chose timeline model")
                .createdAt(createdAt)
                .build();

        var entry = mapper.toKnowledgeEventEntry(event);

        assertEquals(TimelineEntryType.KNOWLEDGE_EVENT, entry.type());
        assertEquals(createdAt, entry.timestamp());
        assertEquals("Chose timeline model", entry.title());
        assertEquals("ARCHITECTURE", entry.detail());
    }

    @Test
    void mapsDecisionToEntryWithoutDetail() {
        Instant createdAt = Instant.now();
        Decision decision = Decision.builder()
                .id(UUID.randomUUID())
                .title("Order decision")
                .createdAt(createdAt)
                .build();

        var entry = mapper.toDecisionEntry(decision);

        assertEquals(TimelineEntryType.DECISION, entry.type());
        assertEquals(createdAt, entry.timestamp());
        assertEquals("Order decision", entry.title());
        assertNull(entry.detail());
    }

    @Test
    void mapsCompletedMilestoneToEntry() {
        Instant completedAt = Instant.now();
        Milestone milestone = Milestone.builder()
                .id(UUID.randomUUID())
                .name("MVP")
                .completedAt(completedAt)
                .build();

        var entry = mapper.toMilestoneEntry(milestone);

        assertEquals(TimelineEntryType.MILESTONE_COMPLETED, entry.type());
        assertEquals(completedAt, entry.timestamp());
        assertEquals("MVP", entry.title());
        assertNull(entry.detail());
    }
}