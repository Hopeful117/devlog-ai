package com.hopeful117.devlogai.timeline.mapper;

import com.hopeful117.devlogai.decision.entity.Decision;
import com.hopeful117.devlogai.engineeringevent.EngineeringEvent;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEvent;
import com.hopeful117.devlogai.milestone.entity.Milestone;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.timeline.dto.TimelineEntry;
import com.hopeful117.devlogai.timeline.dto.TimelineEntryType;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface TimelineMapper {

    default TimelineEntry toStoryEntry(EngineeringStory story) {
        return toEntry(
                story.getId(),
                TimelineEntryType.STORY_COMPLETED,
                story.getCompletedAt(),
                story.getTitle(),
                "#" + story.getStoryNumber()
        );
    }

    default TimelineEntry toEngineeringEventEntry(EngineeringEvent event) {
        return toEntry(
                event.getId(),
                TimelineEntryType.ENGINEERING_EVENT,
                event.getOccurredAt(),
                event.getTitle(),
                event.getCategory().name()
        );
    }

    default TimelineEntry toKnowledgeEventEntry(KnowledgeEvent event) {
        return toEntry(
                event.getId(),
                TimelineEntryType.KNOWLEDGE_EVENT,
                event.getCreatedAt(),
                event.getTitle(),
                event.getType().name()
        );
    }

    default TimelineEntry toDecisionEntry(Decision decision) {
        return toEntry(
                decision.getId(),
                TimelineEntryType.DECISION,
                decision.getCreatedAt(),
                decision.getTitle(),
                null
        );
    }

    default TimelineEntry toMilestoneEntry(Milestone milestone) {
        return toEntry(
                milestone.getId(),
                TimelineEntryType.MILESTONE_COMPLETED,
                milestone.getCompletedAt(),
                milestone.getName(),
                null
        );
    }

    default TimelineEntry toEntry(UUID id, TimelineEntryType type, Instant timestamp, String title, String detail) {
        return new TimelineEntry(id, type, timestamp, title, detail);
    }
}