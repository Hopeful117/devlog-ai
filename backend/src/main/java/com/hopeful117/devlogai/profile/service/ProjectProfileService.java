package com.hopeful117.devlogai.profile.service;

import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import java.util.UUID;

public interface ProjectProfileService {
    ProjectProfileResponse build(UUID analysisId);
    ProjectProfileResponse getByAnalysis(UUID analysisId);
    ProjectProfileResponse getLatestByProject(UUID projectId);
}
