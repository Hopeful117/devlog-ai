package com.hopeful117.devlogai.projectstate.mapper;

import com.hopeful117.devlogai.projectstate.dto.inner.StorySummary;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}