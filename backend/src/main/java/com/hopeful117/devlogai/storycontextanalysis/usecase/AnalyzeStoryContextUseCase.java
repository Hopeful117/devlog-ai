package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.*;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.reference.AiReferenceRegistryFactory;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.storycontextagent.StoryContextAgentProtocolV1;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyzeStoryContextUseCase {

    private static final String INTENT_ID = "engineering-story-context-analysis";
    private static final String INTENT_VERSION = "v1";

    private final ProjectRepository projectRepository;
    private final EngineeringStoryRepository storyRepository;
    private final StoryContextPreparationService preparationService;
    private final IntentCatalog intentCatalog;
    private final AiTaskService aiTaskService;
    private final AIEngineClient aiEngineClient;
    private final AiTaskRepository aiTaskRepository;
    private final ObjectMapper objectMapper;
    private final StoryContextDigestService digestService;
    private final StoryContextCallbackIdentityValidator callbackIdentityValidator;
    private final StoryContextCallbackService callbackService;
    private final StoryContextSubmissionService submissionService;
    private final AnalysisRepository analysisRepository;
    private static final TaskSnapshotEvidenceResolver evidenceResolver = new TaskSnapshotEvidenceResolver();

    /** Read-only projection path; it performs no task creation or second selection. */
    public Map<String, Object> project(String projectSlug, UUID storyId, List<String> files) {
        return project(projectSlug, storyId, INTENT_ID, files);
    }

    public Map<String, Object> project(String projectSlug, UUID storyId, String intent, List<String> files) {
        return prepare(projectSlug, storyId, intent, files).projection();
    }

    /** Builds the canonical context and its Story Context Agent projection exactly once. */
    public PreparedStoryContext prepare(String projectSlug, UUID storyId, String intent, List<String> files) {
        return preparationService.prepare(projectSlug, storyId, intent, files);
    }

    public UUID execute(
            String projectSlug,
            UUID storyId,
            List<String> files,
            Map<String, Object> guidance
    ) {
        return execute(projectSlug, storyId, INTENT_ID, files, guidance);
    }

    public UUID execute(
            String projectSlug, UUID storyId, String requestedIntent, List<String> files,
            Map<String, Object> guidance
    ) {
        return execute(projectSlug, storyId, requestedIntent, files, guidance, null);
    }

    @Transactional
    public UUID execute(
            String projectSlug, UUID storyId, String requestedIntent, List<String> files,
            Map<String, Object> guidance, String idempotencyKey
    ) {
        Project project = projectRepository.findBySlug(projectSlug)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectSlug));
        EngineeringStory story = storyId == null ? null : storyRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("EngineeringStory", storyId));
        if (story != null && !story.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("Story does not belong to project");
        }
        if (requestedIntent == null || requestedIntent.isBlank()) {
            throw new IllegalArgumentException("intent is required");
        }
        intentCatalog.resolve(requestedIntent, INTENT_VERSION);

        List<String> requestedFiles = files == null ? List.of() : List.copyOf(files);
        SubmissionIdentity identity = submissionIdentity(
                projectSlug, storyId, requestedIntent, requestedFiles, guidance, idempotencyKey);
        Optional<UUID> existingSubmission = existingSubmission(identity);
        if (existingSubmission.isPresent()) {
            return existingSubmission.get();
        }

        return submitPrepared(
                prepare(projectSlug, storyId, requestedIntent, files), guidance, identity);
    }

    /** Submits a previously prepared context without reconstructing or selecting it again. */
    @Transactional
    public UUID executePrepared(
            PreparedStoryContext prepared, Map<String, Object> guidance, String idempotencyKey) {
        SubmissionIdentity identity = submissionIdentity(
                prepared.projectSlug(), prepared.storyId(), prepared.intent(), prepared.files(),
                guidance, idempotencyKey);
        Optional<UUID> existingSubmission = existingSubmission(identity);
        if (existingSubmission.isPresent()) {
            return existingSubmission.get();
        }

        return submitPrepared(prepared, guidance, identity);
    }

    private UUID submitPrepared(
            PreparedStoryContext prepared, Map<String, Object> guidance, SubmissionIdentity identity) {
        return submissionService.submit(
                prepared, guidance, identity.submissionDigest(), identity.idempotencyKeyHash());
    }

    private SubmissionIdentity submissionIdentity(
            String projectSlug, UUID storyId, String requestedIntent, List<String> files,
            Map<String, Object> guidance, String idempotencyKey) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("projectSlug", projectSlug);
        values.put("storyId", storyId == null ? null : storyId.toString());
        values.put("intent", requestedIntent);
        values.put("files", files);
        values.put("guidance", guidance == null ? Map.of() : guidance);
        return new SubmissionIdentity(
                digestService.sha256(digestService.canonicalJson(values)),
                idempotencyKey == null || idempotencyKey.isBlank() ? null : digestService.sha256(idempotencyKey));
    }

    private Optional<UUID> existingSubmission(SubmissionIdentity identity) {
        aiTaskRepository.acquireSubmissionLock(identity.idempotencyKeyHash() == null
                ? identity.submissionDigest() : identity.idempotencyKeyHash());
        if (identity.idempotencyKeyHash() != null) {
            Optional<AiTask> existing = aiTaskRepository.findByIdempotencyKeyHash(identity.idempotencyKeyHash());
            if (existing.isPresent()) {
                if (!Objects.equals(existing.get().getSubmissionDigest(), identity.submissionDigest())) {
                    throw new com.hopeful117.devlogai.shared.exception.ConflictException(
                            "Idempotency-Key was already used for a different request");
                }
                return Optional.of(existing.get().getId());
            }
        }
        return aiTaskRepository.findBySubmissionDigest(identity.submissionDigest()).map(AiTask::getId);
    }

    private record SubmissionIdentity(String submissionDigest, String idempotencyKeyHash) { }

    @Transactional
    public void handleCallback(UUID correlationId, AiTaskResultRequest request) {
        callbackService.handle(correlationId, request);
    }

    public void validateCoreIssuedIdentities(AiTask task, PromptExecutionMetadata metadata) {
        callbackIdentityValidator.validate(task, metadata);
    }

    /**
     * Authoritative Java validation of Story Context Analysis result.
     * Validates grounding, trust, relationships, classification, and digest consistency.
     * Per Story 0112 D14 and ADR-067: Java/Core is sole grounding authority.
     */
    static StoryContextAnalysisResult validateStoryContextAnalysisResult(StoryContextAnalysisResult result, AiTask task) {
        return StoryContextResultValidator.validate(result, task);
    }

    @SuppressWarnings("unchecked")
    static Set<String> allowedGroundingReferences(Map<String, Object> groundingContract, AiTask task) {
        if (groundingContract.containsKey("allowedEvidenceReferences")
                || "legacy-grounding/v0".equals(groundingContract.get("groundingContractVersion"))) {
            throw new IllegalStateException("legacy grounding is forbidden in Story Context V1");
        }
        Object typed = groundingContract.get("allowedGroundingReferences");
        if (typed instanceof List<?> entries) {
            Set<String> refs = new LinkedHashSet<>();
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> map)) {
                    throw new IllegalStateException("Typed grounding reference must be an object");
                }
                Object type = map.get("type");
                Object ref = map.get("ref");
                if (!"REPOSITORY_EVIDENCE".equals(type) || !(ref instanceof String value)
                        || value.isBlank() || !"PROJECT_REVISION".equals(map.get("scope"))
                        || !(map.get("project") instanceof String project) || project.isBlank()
                        || !(map.get("revision") instanceof String revision) || revision.isBlank()
                        || !(map.get("provenance") instanceof Map<?, ?>)
                        || !(map.get("trust") instanceof String trust) || trust.isBlank()
                        || !Objects.equals(ref, map.get("coreReference"))
                        || !Objects.equals(ref, map.get("taskReference"))) {
                    throw new IllegalStateException("Invalid typed grounding reference");
                }
                Object snapshotScope = taskSnapshotScope(task);
                if (!(snapshotScope instanceof Map<?, ?> requested)
                        || !Objects.equals(project, requested.get("projectSlug"))) {
                    throw new IllegalStateException("Grounding project does not match requested scope");
                }
                if (!Objects.equals(revision, snapshotRevision(task))) throw new IllegalStateException("Grounding revision does not match PROJECT_REVISION snapshot");
                Map<String, Object> evidence = snapshotEvidence(task, value);
                if (evidence == null || !Objects.equals(map.get("provenance"), evidence.get("provenance"))) throw new IllegalStateException("Grounding provenance does not match snapshot evidence");
                refs.add(value);
            }
            return refs;
        }
        if (!"story-context-grounding/v1".equals(groundingContract.get("groundingContractVersion"))) {
            throw new IllegalStateException("V1 grounding requires the typed allow-list contract");
        }
        throw new IllegalStateException("V1 grounding typed allow-list is missing or invalid");
    }

    private static String snapshotRevision(AiTask task) {
        Object raw = task.getSelectedKnowledgeSnapshot();
        if (!(raw instanceof Map<?, ?> snapshot)) throw new IllegalStateException("Selected snapshot is missing");
        Object freshness = snapshot.get("freshness");
        if (freshness instanceof Map<?, ?> f && f.get("sourceRevision") instanceof Map<?, ?> source && source.get("revision") instanceof String revision && !revision.isBlank()) return revision;
        throw new IllegalStateException("PROJECT_REVISION snapshot revision is missing");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> snapshotEvidence(AiTask task, String reference) {
        Object raw = task.getSelectedKnowledgeSnapshot();
        if (!(raw instanceof Map<?, ?> snapshot)) return null;
        Object context = snapshot.get("context");
        Object entries = context instanceof Map<?, ?> c ? c.get("repositoryEvidence") : null;
        if (!(entries instanceof List<?>)) {
            context = snapshot.get("repositoryContext");
            entries = context instanceof Map<?, ?> c ? c.get("evidence") : null;
        }
        if (!(entries instanceof List<?> list)) return null;
        for (Object entry : list) if (entry instanceof Map<?, ?> e && Objects.equals(reference, e.get("reference"))) return (Map<String, Object>) e;
        return null;
    }

    private static Object taskSnapshotScope(AiTask task) {
        return task.getContextSnapshot() == null ? null : task.getContextSnapshot().get("scope");
    }

    @SuppressWarnings("unchecked")
    static StoryContextAnalysisResult validateV2CausalAssessment(
            StoryContextAnalysisResult result,
            AiTask task,
            Map<String, Object> groundingContract
    ) {
        boolean required = Boolean.TRUE.equals(groundingContract.get("causalAnswerRequired"));
        StoryContextAnalysisResult.CausalAssessment assessment = result.causalAssessment();
        if (required && assessment == null) {
            throw new IllegalStateException("causal answer is required; causalAssessment must not be null");
        }
        if (assessment == null) return result;
        StoryContextAnalysisResult.CausalQuestion expected = causalQuestion(groundingContract);
        if (!sameQuestion(expected, assessment.question())) {
            throw new IllegalStateException("causalAssessment question does not match the Core-owned CausalQuestion");
        }
        if (!result.causalClaims().isEmpty()) {
            throw new IllegalStateException("V2 causal output must not contain legacy causalClaims");
        }
        StoryContextAnalysisResult.CausalAssessment bound = evidenceResolver.bind(
                assessment, task);
        validateV2Admissibility(bound);
        return new StoryContextAnalysisResult(
                result.objectiveUnderstanding(), result.architectureFindings(), result.decisionFindings(),
                result.evidenceFindings(), result.historicalContext(), result.constraintFindings(),
                result.impactedComponentFindings(), result.uncertainties(), result.missingInformation(),
                result.implementationQuestions(), result.confidence(), result.provenance(),
                result.outputClassification(), List.of(), bound);
    }

    @SuppressWarnings("unchecked")
    private static StoryContextAnalysisResult.CausalQuestion causalQuestion(Map<String, Object> contract) {
        Object raw = contract.get("causalQuestion");
        if (!(raw instanceof Map<?, ?> question)) {
            throw new IllegalStateException("V2 causalQuestion is missing");
        }
        Object source = question.get("source");
        Object target = question.get("target");
        Object relation = question.get("relationAsked");
        Object answerRequired = question.get("answerRequired");
        if (!(source instanceof String sourceValue) || !(target instanceof String targetValue)
                || !(relation instanceof String relationValue)) {
            throw new IllegalStateException("V2 causalQuestion is malformed");
        }
        return new StoryContextAnalysisResult.CausalQuestion(sourceValue, targetValue, relationValue,
                Boolean.TRUE.equals(answerRequired));
    }

    private static boolean sameQuestion(
            StoryContextAnalysisResult.CausalQuestion expected,
            StoryContextAnalysisResult.CausalQuestion actual
    ) {
        return expected.source().equals(actual.source())
                && expected.target().equals(actual.target())
                && expected.relationAsked().equals(actual.relationAsked())
                && expected.answerRequired() == actual.answerRequired();
    }

    private static void validateV2Admissibility(StoryContextAnalysisResult.CausalAssessment assessment) {
        var assertions = assessment.evidenceAssertions();
        if (assessment.classification() == StoryContextAnalysisResult.CausalClassification.EXPLICITLY_DOCUMENTED
                && assertions.stream().noneMatch(assertion ->
                assertion.assertionRole() == EvidenceRef.CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT)) {
            throw new IllegalStateException("EXPLICITLY_DOCUMENTED requires an inspectable direct assertion");
        }
        if (assessment.classification() == StoryContextAnalysisResult.CausalClassification.STRONGLY_SUPPORTED) {
            long distinctReferences = assertions.stream().map(assertion -> assertion.evidenceReference().reference())
                    .distinct().count();
            long distinctDigests = assertions.stream().map(StoryContextAnalysisResult.EvidenceAssertion::resolvedContentDigest)
                    .distinct().count();
            if (distinctReferences < 2 || distinctDigests < 2) {
                throw new IllegalStateException("STRONGLY_SUPPORTED requires two distinct resolved assertions");
            }
        }
    }

    static void validateCausalClaims(
            List<StoryContextAnalysisResult.CausalClaim> claims,
            Set<String> allowedRefs,
            Map<String, Object> groundingContract
    ) {
        if (Boolean.TRUE.equals(groundingContract.get("causalAnswerRequired")) && claims.isEmpty()) {
            throw new IllegalStateException("causal answer is required; causalClaims must not be empty");
        }
        Object relationshipValue = groundingContract.get("causalRelationship");
        if (Boolean.TRUE.equals(groundingContract.get("causalAnswerRequired"))
                && relationshipValue instanceof Map<?, ?> relationship
                && relationship.get("source") instanceof String source
                && relationship.get("target") instanceof String target
                && (claims.size() != 1
                || !source.equals(claims.getFirst().source())
                || !target.equals(claims.getFirst().target()))) {
            throw new IllegalStateException("causal answer must contain exactly the required relationship: "
                    + source + " -> " + target);
        }
        Set<String> relationships = new HashSet<>();
        for (StoryContextAnalysisResult.CausalClaim claim : claims) {
            String relationship = claim.source() + "\u0000" + claim.target();
            if (!relationships.add(relationship)) {
                throw new IllegalStateException("Duplicate causal relationship: "
                        + claim.source() + " -> " + claim.target());
            }
            if (claim.causalClassification() != StoryContextAnalysisResult.CausalClassification.NOT_ESTABLISHED
                    && claim.evidenceReferences().isEmpty()) {
                throw new IllegalStateException("Affirmative causal claim requires evidence references: "
                        + claim.source() + " -> " + claim.target());
            }
            boolean affirmativeBasis = claim.evidenceBasis()
                    == StoryContextAnalysisResult.CausalEvidenceBasis.DIRECT_DOCUMENTATION
                    || claim.evidenceBasis()
                    == StoryContextAnalysisResult.CausalEvidenceBasis.MATERIAL_CORROBORATION;
            if (claim.causalClassification()
                    == StoryContextAnalysisResult.CausalClassification.NOT_ESTABLISHED
                    && affirmativeBasis) {
                throw new IllegalStateException("NOT_ESTABLISHED causal claim has affirmative evidence basis");
            }
            for (EvidenceRef evidenceRef : claim.evidenceReferences()) {
                if (!allowedRefs.contains(evidenceRef.reference())) {
                    throw new IllegalStateException(
                            "Causal claim references unauthorized evidence: " + evidenceRef.reference());
                }
            }
            StoryContextAnalysisResult.CausalClassification maximum = maximumDefensibleClassification(
                    claim.evidenceReferences());
            if (causalRank(claim.causalClassification()) > causalRank(maximum)) {
                throw new IllegalStateException("Causal claim exceeds defensible evidence level: "
                        + claim.source() + " -> " + claim.target());
            }
        }
    }

    private static StoryContextAnalysisResult.CausalClassification maximumDefensibleClassification(
            List<EvidenceRef> evidenceReferences
    ) {
        if (evidenceReferences.isEmpty() || evidenceReferences.stream().anyMatch(reference ->
                reference.role() == EvidenceRef.CausalEvidenceRole.NON_CAUSAL_CONTEXT
                        || reference.role() == EvidenceRef.CausalEvidenceRole.CONTRADICTORY_EVIDENCE)) {
            return StoryContextAnalysisResult.CausalClassification.NOT_ESTABLISHED;
        }
        if (evidenceReferences.stream().anyMatch(reference ->
                reference.role() == EvidenceRef.CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT)) {
            return StoryContextAnalysisResult.CausalClassification.EXPLICITLY_DOCUMENTED;
        }
        long materialReferences = evidenceReferences.stream()
                .filter(reference -> reference.role() == EvidenceRef.CausalEvidenceRole.MATERIAL_RELATIONSHIP_SUPPORT)
                .map(EvidenceRef::reference).distinct().count();
        return materialReferences >= 2
                ? StoryContextAnalysisResult.CausalClassification.STRONGLY_SUPPORTED
                : StoryContextAnalysisResult.CausalClassification.NOT_ESTABLISHED;
    }

    private static int causalRank(StoryContextAnalysisResult.CausalClassification classification) {
        return switch (classification) {
            case NOT_ESTABLISHED -> 0;
            case STRONGLY_SUPPORTED -> 1;
            case EXPLICITLY_DOCUMENTED -> 2;
        };
    }

    static void validateGroundedFindings(
            List<? extends Record> findings,
            Set<String> allowedRefs,
            boolean relationTypeRequired
    ) {
        for (Record finding : findings) {
            try {
                // Use reflection to access grounding() method
                var groundingMethod = finding.getClass().getMethod("grounding");
                Object grounding = groundingMethod.invoke(finding);

                // Access evidenceReferences from grounding
                var evidenceRefsMethod = grounding.getClass().getMethod("evidenceReferences");
                @SuppressWarnings("unchecked")
                List<EvidenceRef> evidenceRefs = (List<EvidenceRef>) evidenceRefsMethod.invoke(grounding);

                for (EvidenceRef evidenceRef : evidenceRefs) {
                if (!allowedRefs.contains(evidenceRef.reference())) {
                        String title = findingLabel(finding);
                        throw new IllegalStateException(
                                "Finding '" + title + "' references unauthorized evidence: " + evidenceRef.reference()
                        );
                    }
                }

                // Validate classification
                var classificationMethod = grounding.getClass().getMethod("classification");
                String classification = (String) classificationMethod.invoke(grounding);
                if (!Set.of("FACTUAL_EXTRACTION", "AI_INTERPRETATION", "RECOMMENDATION").contains(classification)) {
                    String title = findingLabel(finding);
                    throw new IllegalStateException(
                            "Finding '" + title + "' has invalid classification: " + classification
                    );
                }

                // Factual/Interpretative findings must be grounded with at least one evidence reference
                if (("FACTUAL_EXTRACTION".equals(classification) || "AI_INTERPRETATION".equals(classification))
                        && evidenceRefs.isEmpty()) {
                    String title = findingLabel(finding);
                    throw new IllegalStateException(
                            "Finding '" + title + "' of type " + classification + " must have at least one evidence reference"
                    );
                }

                // Validate relationType
                var relationTypeMethod = grounding.getClass().getMethod("relationType");
                Object relationType = relationTypeMethod.invoke(grounding);
                if (relationTypeRequired && relationType == null) {
                    String title = findingLabel(finding);
                    throw new IllegalStateException(
                        "Finding '" + title + "' must have relationType"
                    );
                }
                if (relationType == null) {
                    continue;
                }
                if (!Set.of("EXPLICIT", "TEMPORAL_PROXIMITY", "POSSIBLE_RELEVANCE", "INFERRED_HYPOTHESIS")
                        .contains(relationType.toString())) {
                    String title = findingLabel(finding);
                    throw new IllegalStateException(
                            "Finding '" + title + "' has invalid relationType: " + relationType
                    );
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Validation failed for finding: " + e.getMessage(), e);
            }
        }
    }

    private static String findingLabel(Record finding) throws ReflectiveOperationException {
        for (String methodName : List.of("title", "componentName")) {
            try {
                return (String) finding.getClass().getMethod(methodName).invoke(finding);
            } catch (NoSuchMethodException ignored) {
                // Finding categories use either title or componentName as their display label.
            }
        }
        throw new IllegalStateException("Finding has no display label: " + finding.getClass().getSimpleName());
    }
}
