package com.hopeful117.devlogai.timeline.service;

import com.hopeful117.devlogai.decision.repository.DecisionRepository;
import com.hopeful117.devlogai.engineeringevent.EngineeringEventRepository;
import com.hopeful117.devlogai.knowledge.repository.KnowledgeEventRepository;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;
import com.hopeful117.devlogai.milestone.repository.MilestoneRepository;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import com.hopeful117.devlogai.timeline.dto.TimelineEntry;
import com.hopeful117.devlogai.timeline.dto.TimelineResponse;
import com.hopeful117.devlogai.timeline.mapper.TimelineMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TimelineProjectionServiceImpl implements TimelineProjectionService {

    private static final int PER_SOURCE_LIMIT = 20;
    private static final int GLOBAL_LIMIT = 20;

    private final ProjectRepository projectRepository;
    private final EngineeringStoryRepository storyRepository;
    private final EngineeringEventRepository engineeringEventRepository;
    private final KnowledgeEventRepository knowledgeEventRepository;
    private final DecisionRepository decisionRepository;
    private final MilestoneRepository milestoneRepository;
    private final TimelineMapper mapper;

    @Override
    public TimelineResponse getTimeline(UUID projectId) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectId));

        var page = PageRequest.of(0, PER_SOURCE_LIMIT);

        var entries = new ArrayList<TimelineEntry>();
        entries.addAll(storyRepository
                .findByProject_IdAndStatusOrderByCompletedAtDescIdDesc(projectId, StoryStatus.COMPLETED, page)
                .stream()
                .filter(story -> story.getCompletedAt() != null)
                .map(mapper::toStoryEntry)
                .toList());
        entries.addAll(engineeringEventRepository
                .findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(projectId, page)
                .stream()
                .map(mapper::toEngineeringEventEntry)
                .toList());
        entries.addAll(knowledgeEventRepository
                .findByProjectIdOrderByCreatedAtDescIdDesc(projectId, page)
                .stream()
                .map(mapper::toKnowledgeEventEntry)
                .toList());
        entries.addAll(decisionRepository
                .findByProjectIdOrderByCreatedAtDescIdDesc(projectId, page)
                .stream()
                .map(mapper::toDecisionEntry)
                .toList());
        entries.addAll(milestoneRepository
                .findByProjectIdAndStatusOrderByCompletedAtDescIdDesc(projectId, MilestoneStatus.COMPLETED, page)
                .stream()
                .filter(milestone -> milestone.getCompletedAt() != null)
                .map(mapper::toMilestoneEntry)
                .toList());

        entries.sort(COMPARATOR);

        List<TimelineEntry> limited = entries.size() > GLOBAL_LIMIT
                ? List.copyOf(entries.subList(0, GLOBAL_LIMIT))
                : List.copyOf(entries);

        return new TimelineResponse(project.getId(), project.getName(), limited);
    }

    private static final Comparator<TimelineEntry> COMPARATOR = Comparator
            .comparing(TimelineEntry::timestamp, Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed()
            .thenComparing(entry -> entry.type().name())
            .thenComparing(TimelineEntry::id);
}