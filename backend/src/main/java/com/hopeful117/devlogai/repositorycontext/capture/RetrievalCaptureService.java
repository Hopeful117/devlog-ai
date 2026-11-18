package com.hopeful117.devlogai.repositorycontext.capture;

import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Opt-in capture of live {@link com.hopeful117.devlogai.repositorycontext.RepositoryContextEngine}
 * outputs for the Story 0206 live evaluation harness. Disabled by default.
 * Capture failure is logged and swallowed — it must never affect the caller.
 */
@Service
public class RetrievalCaptureService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalCaptureService.class);
    static final String PROTOCOL_VERSION = "story-0206-live-capture/v1";

    @Value("${devlog.retrieval.capture.enabled:false}")
    private boolean enabled;

    @Value("${devlog.retrieval.capture.output-dir:evaluations/captures/live}")
    private String outputDir;

    private final ObjectMapper objectMapper;

    public RetrievalCaptureService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Records a bounded capture of the retrieval pipeline output when captures are enabled.
     *
     * @param question    intent objective used as the query
     * @param projectSlug project identifier
     * @param revision    revision scope (empty string when not scoped)
     * @param ctx         the built RepositoryContext (selected evidence + diagnostics)
     * @param ranked      full ranked candidate list before selection
     * @param latencyMs   wall-clock time for the full build() call
     */
    public void capture(
            String question,
            String projectSlug,
            String revision,
            RepositoryContext ctx,
            List<RepositoryEvidence> ranked,
            long latencyMs
    ) {
        if (!enabled) return;
        try {
            doCaptureUnchecked(question, projectSlug, revision, ctx, ranked, latencyMs);
        } catch (Exception e) {
            log.warn("Retrieval capture failed and was suppressed: {}", e.getMessage(), e);
        }
    }

    private void doCaptureUnchecked(
            String question,
            String projectSlug,
            String revision,
            RepositoryContext ctx,
            List<RepositoryEvidence> ranked,
            long latencyMs
    ) throws IOException {
        String scenarioId = UUID.randomUUID().toString();

        Set<String> selectedRefs = ctx.evidence().stream()
                .map(RepositoryEvidence::reference)
                .collect(Collectors.toSet());

        Map<String, RepositoryContext.SelectionDecision> decisionByRef =
                ctx.selectionDecisions().stream()
                        .collect(Collectors.toMap(
                                RepositoryContext.SelectionDecision::evidenceReference,
                                d -> d,
                                (a, b) -> a));

        List<CaptureRecord.CandidateCapture> candidates = new ArrayList<>();
        for (RepositoryEvidence evidence : ranked) {
            String ref = evidence.reference();
            boolean isSelected = selectedRefs.contains(ref);
            RepositoryContext.SelectionDecision decision = decisionByRef.get(ref);
            String rejectionReason = (decision != null && !decision.selected())
                    ? decision.reason() : null;
            String sourceType = evidence.provenance() != null
                    ? evidence.provenance().sourceType() : null;
            String trustTier = evidence.extractionMetadata() != null
                    ? evidence.extractionMetadata().get("trustTier") : null;
            String status = evidence.extractionMetadata() != null
                    ? evidence.extractionMetadata().getOrDefault("status", "CURRENT") : "CURRENT";
            candidates.add(new CaptureRecord.CandidateCapture(
                    ref,
                    evidence.kind(),
                    trustTier,
                    status,
                    sourceType,
                    evidence.relevanceScore(),
                    isSelected,
                    rejectionReason
            ));
        }

        int estimatedTokens = ctx.evidence().stream()
                .mapToInt(RepositoryEvidence::estimatedTokens)
                .sum();

        CaptureRecord record = new CaptureRecord(
                PROTOCOL_VERSION,
                Instant.now().toString(),
                scenarioId,
                projectSlug,
                revision,
                question,
                ranked.size(),
                ctx.evidence().size(),
                estimatedTokens,
                ctx.truncated(),
                latencyMs,
                ctx.contextDigest(),
                List.of(),
                candidates
        );

        Path dir = Path.of(outputDir);
        Files.createDirectories(dir);
        Path file = dir.resolve(System.currentTimeMillis() + "-" + scenarioId + ".json");
        objectMapper.writeValue(file.toFile(), record);
        log.debug("Retrieval capture written to {}", file);
    }
}
