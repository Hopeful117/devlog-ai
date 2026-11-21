package com.hopeful117.devlogai.story.service;

import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.project.entity.Project;

import java.util.UUID;

public interface StoryCatalogSynchronizationService {

    StoryCatalogSynchronizationResult synchronize(Project project, SynchronizedWorkspace workspace);

    StoryCatalogSynchronizationResult synchronize(UUID projectId, String targetRevision);
}
