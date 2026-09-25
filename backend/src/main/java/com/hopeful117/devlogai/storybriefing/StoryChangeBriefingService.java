package com.hopeful117.devlogai.storybriefing;

import java.util.UUID;

public interface StoryChangeBriefingService {
    StoryChangeBriefing build(String projectSlug, UUID storyId, String description);
}
