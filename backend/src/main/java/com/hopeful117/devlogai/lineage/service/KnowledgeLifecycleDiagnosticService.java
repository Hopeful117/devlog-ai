package com.hopeful117.devlogai.lineage.service;

import com.hopeful117.devlogai.lineage.dto.KnowledgeLifecycleDiagnosticResponse;

import java.util.UUID;

public interface KnowledgeLifecycleDiagnosticService {
    KnowledgeLifecycleDiagnosticResponse diagnose(UUID proposalId);
}