package com.hopeful117.devlogai.evidence.retrieval;

import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import com.hopeful117.devlogai.history.service.ProjectHistoryQueryMatch;
import com.hopeful117.devlogai.history.service.ProjectHistoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Adapts deterministic persisted project-history matches to retrieval candidates. */
@Service
@RequiredArgsConstructor
public class GitCommitRetrievalAdapter implements GitCommitRetrievalPort {
    private static final int MAX_SUMMARY_LENGTH = 255;
    private static final EvidenceCandidateProvenance PROVENANCE =
            new EvidenceCandidateProvenance("PROJECT_HISTORY");

    private final ProjectHistoryQueryService historyQueryService;

    @Override
    public EvidencePage retrieve(EvidenceRetrievalQuery query) {
        List<ProjectHistoryQueryMatch> matches = historyQueryService.findMatches(
                query.scope().projectId(), query.scope().sourceId(), query.lexicalQuery());
        long totalMatches = matches.size();
        long from = (long) query.page() * query.pageSize();
        if (from >= totalMatches) {
            return new EvidencePage(List.of(), query.page(), query.pageSize(), totalMatches, false);
        }

        int start = (int) from;
        int end = Math.min(start + query.pageSize(), matches.size());
        List<EvidenceCandidate> candidates = matches.subList(start, end).stream()
                .map(match -> toCandidate(query.scope().projectId(), match))
                .toList();
        return new EvidencePage(candidates, query.page(), query.pageSize(), totalMatches,
                end < totalMatches);
    }

    private EvidenceCandidate toCandidate(
            java.util.UUID projectId, ProjectHistoryQueryMatch match) {
        String reference = "git:" + match.sourceId() + ":" + match.commitSha();
        return new EvidenceCandidate(
                reference,
                EvidenceRetrievalFamily.GIT_COMMIT,
                projectId,
                match.sourceId(),
                match.committedAt(),
                boundedSummary(match.subject()),
                PROVENANCE,
                TrustTier.TECHNICAL_EVIDENCE);
    }

    private String boundedSummary(String subject) {
        if (subject == null || subject.length() <= MAX_SUMMARY_LENGTH) return subject;
        return subject.substring(0, MAX_SUMMARY_LENGTH - 3) + "...";
    }
}
