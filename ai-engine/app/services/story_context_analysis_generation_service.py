from datetime import datetime, timezone
import logging
from uuid import UUID

from pydantic import ValidationError

from app.clients.core_callback_client import CoreCallbackClient
from app.models.ai_task import AiTaskType
from app.models.proposal import AiTaskResultStatus
from app.prompts.story_context_analysis import StoryContextAnalysisPromptBuilder
from app.providers.base import LlmProvider, Prompt
from app.schemas.ai_task import AiTaskSubmissionRequest
from app.schemas.ai_task_result import (
    AiTaskResultError,
    AiTaskResultRequest,
    PromptExecutionMetadata,
)
from app.schemas.story_context_analysis import (
    CausalEvidenceBasis,
    CausalClassification,
    CausalEvidenceRole,
    StoryContextAnalysisResult,
    RelationType,
    maximum_defensible_classification,
    CausalAssessment,
)
from app.services.interaction_trace import InteractionTraceCollector


class StoryContextAnalysisOutputValidationError(ValueError):
    failure_category = "STRUCTURAL_CONTRACT_ERROR"


class StoryContextAnalysisGroundingError(StoryContextAnalysisOutputValidationError):
    failure_category = "GROUNDING_ERROR"


class StoryContextAnalysisSemanticSupportError(StoryContextAnalysisOutputValidationError):
    failure_category = "SEMANTIC_SUPPORT_ERROR"


# Findings that express relationships and must have relationType
RELATIONSHIP_BEARING_FINDING_TYPES = {
    "architecture_findings",
    "decision_findings",
    "historical_context",
    "impacted_component_findings",
}

VALID_RELATION_TYPES = {rt.value for rt in RelationType}


logger = logging.getLogger(__name__)


class StoryContextAnalysisGenerationService:
    def __init__(
        self,
        provider: LlmProvider,
        prompt_builder: StoryContextAnalysisPromptBuilder,
        callback_client: CoreCallbackClient,
    ) -> None:
        self._provider = provider
        self._prompt_builder = prompt_builder
        self._callback_client = callback_client

    async def process(
        self,
        submission: AiTaskSubmissionRequest,
        external_job_id: UUID,
    ) -> None:
        try:
            prompt = self._prompt_builder.build(submission)
        except ValueError as intent_error:
            await self._send_failure(
                submission, external_job_id,
                getattr(intent_error, "code", "PROMPT_CONSTRUCTION_FAILED"),
                intent_error,
            )
            return
        traces = InteractionTraceCollector(self._provider, submission)

        try:
            output = await self._generate_and_validate(
                prompt, submission.selected_knowledge, submission.grounding_contract or {}, traces
            )
        except (ValidationError, StoryContextAnalysisOutputValidationError, ValueError) as error:
            corrective_prompt = self._prompt_builder.corrective_retry(prompt, error)
            traces.retry(error)
            try:
                output = await self._generate_and_validate(
                    corrective_prompt, submission.selected_knowledge, submission.grounding_contract or {}, traces
                )
                prompt = corrective_prompt
            except (ValidationError, StoryContextAnalysisOutputValidationError, ValueError) as retry_error:
                await self._send_failure(
                    submission, external_job_id, "INVALID_LLM_OUTPUT", retry_error, corrective_prompt, traces
                )
                return
            except Exception as provider_error:
                await self._send_failure(
                    submission, external_job_id, "LLM_PROVIDER_ERROR", provider_error, corrective_prompt, traces
                )
                return
        except Exception as provider_error:
            await self._send_failure(
                submission, external_job_id, "LLM_PROVIDER_ERROR", provider_error, prompt, traces
            )
            return

        logger.info(
            "Story context analysis generation completed promptVersion=%s promptDigest=%s provider=%s model=%s",
            prompt.prompt_version, prompt.content_digest, self._provider.provider_name,
            self._provider.model_identifier,
        )

        await self._callback_client.send_result(
            submission.correlation_id,
            AiTaskResultRequest(
                correlation_id=submission.correlation_id,
                external_job_id=str(external_job_id),
                status=AiTaskResultStatus.COMPLETED,
                completed_at=datetime.now(timezone.utc),
                proposals=[],
                error=None,
                prompt_execution=PromptExecutionMetadata(
                    prompt_version=prompt.prompt_version,
                    provider=self._provider.provider_name,
                    model_identifier=self._provider.model_identifier,
                    prompt_content_digest=prompt.content_digest,
                    context_digest=prompt.traceability.context_digest,
                ),
                synthesis=None,
                analysis_result=output,
                interaction_traces=traces.traces,
            ),
        )

    async def _generate_and_validate(
        self,
        prompt: Prompt,
        context: dict[str, object],
        grounding_contract: dict[str, object],
        traces: InteractionTraceCollector,
    ) -> StoryContextAnalysisResult:
        return await traces.generate_and_validate(
            prompt,
            StoryContextAnalysisResult,
            lambda output: self._validate_output(output, context, grounding_contract),
        )

    def _validate_output(
        self,
        output: StoryContextAnalysisResult,
        context: dict[str, object],
        grounding_contract: dict[str, object],
    ) -> None:
        # Use Java-authored grounding contract (authoritative per Story 0112 D14)
        allowed_refs = set()
        allowed_list = grounding_contract.get("allowedEvidenceReferences", [])
        if isinstance(allowed_list, list):
            for ref in allowed_list:
                if isinstance(ref, str):
                    allowed_refs.add(ref)

        all_findings = (
            output.architecture_findings
            + output.decision_findings
            + output.evidence_findings
            + output.historical_context
            + output.constraint_findings
            + output.impacted_component_findings
        )

        causal_assessment = getattr(output, "causal_assessment", None)
        if grounding_contract.get("causalContractVersion") == "V2":
            self._validate_v2_assessment(causal_assessment, output, allowed_refs, grounding_contract)
        causal_claims = output.causal_claims
        if grounding_contract.get("causalContractVersion") != "V2" \
                and grounding_contract.get("causalAnswerRequired") is True and not causal_claims:
            raise StoryContextAnalysisOutputValidationError(
                "causal answer is required; causalClaims must contain an explicit claim"
            )
        required_relationship = grounding_contract.get("causalRelationship")
        if grounding_contract.get("causalContractVersion") != "V2" \
                and grounding_contract.get("causalAnswerRequired") is True and isinstance(required_relationship, dict):
            source = required_relationship.get("source")
            target = required_relationship.get("target")
            if isinstance(source, str) and isinstance(target, str):
                if len(causal_claims) != 1 or (causal_claims[0].source, causal_claims[0].target) != (source, target):
                    raise StoryContextAnalysisOutputValidationError(
                        f"causal answer must contain exactly the required relationship: {source} -> {target}"
                    )
        seen_pairs: set[tuple[str, str]] = set()
        for claim in causal_claims:
            pair = (claim.source, claim.target)
            if pair in seen_pairs:
                raise StoryContextAnalysisOutputValidationError(
                    f"Duplicate causal relationship: {claim.source} -> {claim.target}"
                )
            seen_pairs.add(pair)
            if claim.source == claim.target:
                raise StoryContextAnalysisOutputValidationError(
                    f"Causal claim source and target must differ: {claim.source}"
                )
            if claim.causal_classification in {
                CausalClassification.EXPLICITLY_DOCUMENTED,
                CausalClassification.STRONGLY_SUPPORTED,
            } and not claim.evidence_references:
                raise StoryContextAnalysisGroundingError(
                    f"Affirmative causal claim {claim.source} -> {claim.target} requires evidence"
                )
            if claim.causal_classification.value == "NOT_ESTABLISHED" and claim.evidence_basis in {
                CausalEvidenceBasis.DIRECT_DOCUMENTATION,
                CausalEvidenceBasis.MATERIAL_CORROBORATION,
            }:
                raise StoryContextAnalysisSemanticSupportError(
                    f"NOT_ESTABLISHED causal claim {claim.source} -> {claim.target} has affirmative evidenceBasis"
                )
            claim_refs = {er.reference for er in claim.evidence_references}
            unknown = claim_refs - allowed_refs
            if unknown:
                raise StoryContextAnalysisGroundingError(
                    f"Causal claim {claim.source} -> {claim.target} references unknown evidence: {sorted(unknown)}"
                )
            if any(er.role is None for er in claim.evidence_references):
                raise StoryContextAnalysisOutputValidationError(
                    f"Causal claim {claim.source} -> {claim.target} requires an evidence role for every reference"
                )
            maximum = maximum_defensible_classification(claim.evidence_references)
            rank = {
                CausalClassification.NOT_ESTABLISHED: 0,
                CausalClassification.STRONGLY_SUPPORTED: 1,
                CausalClassification.EXPLICITLY_DOCUMENTED: 2,
            }
            if rank[claim.causal_classification] > rank[maximum]:
                raise StoryContextAnalysisSemanticSupportError(
                    f"Causal claim {claim.source} -> {claim.target} classification exceeds defensible evidence level: "
                    f"selected={claim.causal_classification.value}, maximum={maximum.value}"
                )
            if claim.causal_classification is CausalClassification.STRONGLY_SUPPORTED:
                material_refs = {
                    er.reference for er in claim.evidence_references
                    if er.role is CausalEvidenceRole.MATERIAL_RELATIONSHIP_SUPPORT
                }
                if len(material_refs) < 2:
                    raise StoryContextAnalysisSemanticSupportError(
                        f"Causal claim {claim.source} -> {claim.target} requires two distinct material supports"
                    )

        for finding in all_findings:
            finding_refs = {er.reference for er in finding.grounding.evidence_references}
            unknown = finding_refs - allowed_refs
            if unknown:
                raise StoryContextAnalysisOutputValidationError(
                    f"Finding {finding.title} references unknown evidence: {sorted(unknown)}"
                )
            if finding.grounding.classification not in {"FACTUAL_EXTRACTION", "AI_INTERPRETATION", "RECOMMENDATION"}:
                raise StoryContextAnalysisOutputValidationError(
                    f"Invalid classification: {finding.grounding.classification}"
                )
            if finding.grounding.classification in {"FACTUAL_EXTRACTION", "AI_INTERPRETATION"} and not finding.grounding.grounded:
                raise StoryContextAnalysisOutputValidationError(
                    f"FACTUAL_EXTRACTION and AI_INTERPRETATION findings must be grounded"
                )
            # Validate relationType for relationship-bearing findings
            finding_type = type(finding).__name__.lower().replace("finding", "_findings").replace("historicalcontextitem", "historical_context")
            if finding_type in RELATIONSHIP_BEARING_FINDING_TYPES:
                if finding.grounding.relation_type is None:
                    raise StoryContextAnalysisGroundingError(
                        f"Finding {finding.title} must have relationType"
                    )
                if finding.grounding.relation_type.value not in VALID_RELATION_TYPES:
                    raise StoryContextAnalysisGroundingError(
                        f"Finding {finding.title} has invalid relationType: {finding.grounding.relation_type.value}"
                    )

        for unc in output.uncertainties:
            for er in unc.related_evidence:
                if er.reference not in allowed_refs:
                    raise StoryContextAnalysisOutputValidationError(
                        f"Uncertainty references unknown evidence: {er.reference}"
                    )

        if output.confidence.level not in {"HIGH", "MEDIUM", "LOW"}:
            raise StoryContextAnalysisOutputValidationError(
                f"Invalid confidence level: {output.confidence.level}"
            )

        for cls in output.output_classification.entries:
            if cls.classification not in {"FACTUAL_EXTRACTION", "AI_INTERPRETATION", "RECOMMENDATION"}:
                raise StoryContextAnalysisOutputValidationError(
                    f"Invalid output classification: {cls.classification}"
                )

    def _validate_v2_assessment(
        self,
        assessment: CausalAssessment | None,
        output: StoryContextAnalysisResult,
        allowed_refs: set[str],
        grounding_contract: dict[str, object],
    ) -> None:
        if assessment is None:
            if grounding_contract.get("causalAnswerRequired") is True:
                raise StoryContextAnalysisOutputValidationError(
                    "causal answer is required; causalAssessment must be present"
                )
            return
        if getattr(output, "causal_claims", []) and grounding_contract.get("causalAnswerRequired") is True:
            raise StoryContextAnalysisOutputValidationError(
                "V2 causal output must not contain legacy causalClaims"
            )
        question = grounding_contract.get("causalQuestion")
        if not isinstance(question, dict):
            raise StoryContextAnalysisOutputValidationError("V2 causalQuestion is missing")
        expected = (
            question.get("source"), question.get("target"),
            question.get("relationAsked"), bool(question.get("answerRequired")),
        )
        actual = (
            assessment.question.source, assessment.question.target,
            assessment.question.relation_asked, assessment.question.answer_required,
        )
        if actual != expected:
            raise StoryContextAnalysisOutputValidationError(
                "causalAssessment question does not match the Core-owned causalQuestion"
            )
        references = [assertion.evidence_reference.reference for assertion in assessment.evidence_assertions]
        unknown = set(references) - allowed_refs
        if unknown:
            raise StoryContextAnalysisGroundingError(
                f"causalAssessment references unknown evidence: {sorted(unknown)}"
            )
        keys = [
            (assertion.evidence_reference.reference, assertion.locator.model_dump_json())
            for assertion in assessment.evidence_assertions
        ]
        if len(keys) != len(set(keys)):
            raise StoryContextAnalysisOutputValidationError("causalAssessment contains duplicate assertions")
        if assessment.classification is CausalClassification.EXPLICITLY_DOCUMENTED:
            if not any(
                assertion.assertion_role is CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT
                for assertion in assessment.evidence_assertions
            ):
                raise StoryContextAnalysisSemanticSupportError(
                    "EXPLICITLY_DOCUMENTED requires a direct evidence assertion"
                )
        if assessment.classification is CausalClassification.STRONGLY_SUPPORTED:
            distinct_references = {
                assertion.evidence_reference.reference
                for assertion in assessment.evidence_assertions
            }
            if len(distinct_references) < 2:
                raise StoryContextAnalysisSemanticSupportError(
                    "STRONGLY_SUPPORTED requires two distinct evidence assertions"
                )

    async def _send_failure(
        self,
        submission: AiTaskSubmissionRequest,
        external_job_id: UUID,
        error_code: str,
        error: Exception,
        prompt: Prompt | None = None,
        traces: InteractionTraceCollector | None = None,
    ) -> None:
        await self._callback_client.send_result(
            submission.correlation_id,
            AiTaskResultRequest(
                correlation_id=submission.correlation_id,
                external_job_id=str(external_job_id),
                status=AiTaskResultStatus.FAILED,
                completed_at=datetime.now(timezone.utc),
                proposals=[],
                error=AiTaskResultError(
                    code=error_code,
                    message=str(error)[:5000] or "LLM output validation failed",
                ),
                prompt_execution=self._execution_metadata(prompt) if prompt else None,
                interaction_traces=traces.traces if traces else [],
            ),
        )

    def _execution_metadata(self, prompt: Prompt) -> PromptExecutionMetadata:
        return PromptExecutionMetadata(
            prompt_version=prompt.prompt_version,
            provider=self._provider.provider_name,
            model_identifier=self._provider.model_identifier,
            prompt_content_digest=prompt.content_digest,
            context_digest=prompt.traceability.context_digest,
        )
