package com.hopeful117.devlogai.storycontextanalysis.investigation;

import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import com.hopeful117.devlogai.engineeringcontext.mapper.TrustTierClassifier;
import com.hopeful117.devlogai.projectcontext.ProjectContextProvider;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.RepositoryContextAdapter;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Executes the small, Core-owned retrieval capability set for the v2 protocol. */
@Service
@RequiredArgsConstructor
public class InvestigationCapabilityExecutionService {
    private final ProjectContextProvider projectContextProvider;
    private final RepositoryContextAdapter repositoryContextAdapter;

    public InvestigationCapabilityResult execute(
            UUID projectId,
            UUID storyId,
            InvestigationAction action,
            InvestigationScope scope,
            InvestigationBudget budget,
            InvestigationUsage usage,
            Set<String> actionIds) {
        if (projectId == null || storyId == null) {
            return InvestigationCapabilityResult.rejected(
                    projectId, storyId, action, "INVALID_INPUT", "Project and Story identifiers are required");
        }
        var validation = InvestigationCapabilityActionValidator.validate(
                action, scope, budget, usage, actionIds,
                InvestigationCapabilityRegistry.phaseOne());
        if (!validation.accepted()) {
            return InvestigationCapabilityResult.rejected(
                    projectId, storyId, action, validation.code(), validation.message());
        }

        try {
            ProjectContextSnapshot snapshot = projectContextProvider.build(projectId);
            RepositoryContext context = repositoryContextAdapter.buildRepositoryContext(
                    projectId,
                    action.purpose(),
                    snapshot,
                    requestedFiles(action),
                    storyId,
                    searchQuestion(action));
            return projectResult(projectId, storyId, action, scope, context);
        } catch (RuntimeException exception) {
            return InvestigationCapabilityResult.rejected(
                    projectId, storyId, action, "RETRIEVAL_FAILED", "Core retrieval was unavailable");
        }
    }

    private InvestigationCapabilityResult projectResult(
            UUID projectId,
            UUID storyId,
            InvestigationAction action,
            InvestigationScope scope,
            RepositoryContext context) {
        if (context == null || context.evidence() == null) {
            return InvestigationCapabilityResult.rejected(
                    projectId, storyId, action, "UNSUPPORTED_RESULT", "Core returned no supported evidence result");
        }

        List<InvestigationCapabilityResult.Evidence> projected = new ArrayList<>();
        Set<String> references = new HashSet<>();
        long remainingBytes = action.maximumBytes();
        long remainingTokens = action.maximumTokens();
        for (RepositoryEvidence evidence : context.evidence()) {
            if (evidence == null || !references.add(evidence.reference())) {
                continue;
            }
            String path = evidence.provenance() == null ? null : evidence.provenance().originatingFile();
            String revision = resolvedRevision(evidence);
            if (!authorizedPath(path, action, scope) || !action.revision().equals(revision)) {
                return InvestigationCapabilityResult.rejected(
                        projectId, storyId, action, "UNAUTHORIZED_RESULT",
                        "Core returned evidence outside the authorized path or revision");
            }
            TrustTier trustTier = TrustTierClassifier.publicTrustTier(
                    evidence.kind(), evidence.provenance().sourceType());
            if (trustTier == null || evidence.reference() == null || evidence.reference().isBlank()) {
                return InvestigationCapabilityResult.rejected(
                        projectId, storyId, action, "UNSUPPORTED_RESULT", "Core returned unsupported evidence metadata");
            }
            String content = null;
            Integer startLine = null;
            Integer endLine = null;
            if (action instanceof ReadEvidenceRangeRequest read) {
                if (evidence.content() == null || evidence.content().text() == null) {
                    return InvestigationCapabilityResult.rejected(
                            projectId, storyId, action, "UNSUPPORTED_RESULT", "Core returned no readable evidence content");
                }
                content = boundedContent(slice(evidence.content().text(), read.startLine(), read.endLine()),
                        remainingBytes, remainingTokens);
                startLine = read.startLine();
                endLine = read.endLine();
            }
            String summary = boundedContent(evidence.summary(), remainingBytes, remainingTokens);
            long summaryBytes = utf8Bytes(summary);
            long summaryTokens = estimateTokens(summaryBytes);
            remainingBytes -= summaryBytes;
            remainingTokens -= summaryTokens;
            if (content != null) {
                content = boundedContent(content, remainingBytes, remainingTokens);
            }
            long payloadBytes = summaryBytes + utf8Bytes(content);
            long payloadTokens = estimateTokens(payloadBytes);
            if (payloadBytes == 0 && projected.size() > 0) break;
            projected.add(new InvestigationCapabilityResult.Evidence(
                    evidence.reference(), evidence.kind(), path, revision,
                    evidence.provenance().sourceType(), evidence.provenance().identifier(),
                    summary, trustTier,
                    path != null && evidence.reference() != null, content, startLine, endLine));
            remainingBytes -= utf8Bytes(content);
            remainingTokens -= estimateTokens(utf8Bytes(content));
            if (projected.size() == action.resultLimit()) break;
        }
        InvestigationCapabilityResult.Outcome outcome = projected.isEmpty()
                ? InvestigationCapabilityResult.Outcome.EMPTY
                : InvestigationCapabilityResult.Outcome.RESULTS;
        return new InvestigationCapabilityResult(projectId, storyId, action.actionId(), action.kind(),
                action.projectSlug(), action.revision(), outcome, projected, null, null);
    }

    private boolean authorizedPath(String path, InvestigationAction action, InvestigationScope scope) {
        if (path == null || path.isBlank()) return false;
        if (action instanceof ReadEvidenceRangeRequest read && !path.equals(read.path())) return false;
        if (action instanceof SearchEvidenceRequest search && search.pathPrefix() != null
                && !(path.equals(search.pathPrefix()) || path.startsWith(search.pathPrefix() + "/"))) return false;
        return scope.allowedPathPrefixes().stream().anyMatch(prefix ->
                path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private String resolvedRevision(RepositoryEvidence evidence) {
        if (evidence.extractionMetadata() != null) {
            String revision = evidence.extractionMetadata().get("resolvedRevision");
            if (revision != null && !revision.isBlank()) return revision;
        }
        return evidence.content() == null ? null : evidence.content().revision();
    }

    private List<String> requestedFiles(InvestigationAction action) {
        return action instanceof ReadEvidenceRangeRequest read ? List.of(read.path()) : List.of();
    }

    private String searchQuestion(InvestigationAction action) {
        return action instanceof SearchEvidenceRequest search ? search.query() : null;
    }

    private String slice(String text, int startLine, int endLine) {
        String[] lines = text.split("\\R", -1);
        int from = startLine - 1;
        if (from >= lines.length) return "";
        int to = Math.min(endLine, lines.length);
        return String.join("\n", java.util.Arrays.copyOfRange(lines, from, to));
    }

    private String boundedContent(String value, long maximumBytes, long maximumTokens) {
        if (value == null || maximumBytes <= 0 || maximumTokens <= 0) return value == null ? null : "";
        long maximumCharacters = Math.min(maximumBytes, maximumTokens * 4L);
        if (value.length() <= maximumCharacters
                && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maximumBytes) {
            return value;
        }
        int end = (int) Math.min(value.length(), maximumCharacters);
        while (end > 0 && value.substring(0, end)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maximumBytes) {
            end--;
        }
        return value.substring(0, end);
    }

    private long utf8Bytes(String value) {
        return value == null ? 0 : value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private long estimateTokens(long bytes) {
        return bytes == 0 ? 0 : (bytes + 3) / 4;
    }
}
