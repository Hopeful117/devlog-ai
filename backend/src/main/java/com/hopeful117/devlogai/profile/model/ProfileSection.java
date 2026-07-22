package com.hopeful117.devlogai.profile.model;

import java.util.List;

public record ProfileSection(ProfileCategory category, List<ProfileCharacteristic> characteristics,
                             String deterministicSummary) {
    public ProfileSection { characteristics = List.copyOf(characteristics); }
}
