package com.hopeful117.devlogai.repositoryevidence;

import com.hopeful117.devlogai.insight.entity.Insight;

import java.util.Optional;

public interface RepositoryEvidenceResolver {

    Optional<RepositoryEvidenceProjection> resolve(Insight insight);
}