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
from app.schemas.story_context_analysis import StoryContextAnalysisResult, RelationType


class StoryContextAnalysisOutputValidationError(ValueError):
    pass


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

        try:
            output = await self._generate_and_validate(prompt, submission.selected_knowledge)
        except (ValidationError, StoryContextAnalysisOutputValidationError, ValueError) as error:
            corrective_prompt = self._prompt_builder.corrective_retry(prompt, error)
            try:
                output = await self._generate_and_validate(corrective_prompt, submission.selected_knowledge)
                prompt = corrective_prompt
            except (ValidationError, StoryContextAnalysisOutputValidationError, ValueError) as retry_error:
                await self._send_failure(
                    submission, external_job_id, "INVALID_LLM_OUTPUT", retry_error, corrective_prompt
                )
                return
            except Exception as provider_error:
                await self._send_failure(
                    submission, external_job_id, "LLM_PROVIDER_ERROR", provider_error, corrective_prompt
                )
                return
        except Exception as provider_error:
            await self._send_failure(
                submission, external_job_id, "LLM_PROVIDER_ERROR", provider_error, prompt
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
            ),
        )

    async def _generate_and_validate(
        self,
        prompt: Prompt,
        context: dict[str, object],
    ) -> StoryContextAnalysisResult:
        output = await self._provider.generate_structured(prompt, StoryContextAnalysisResult)
        validated = StoryContextAnalysisResult.model_validate(output)
        self._validate_output(validated, context)
        return validated

    def _validate_output(
        self,
        output: StoryContextAnalysisResult,
        context: dict[str, object],
    ) -> None:
        repo_context = context.get("repositoryContext", {})
        if not isinstance(repo_context, dict):
            raise StoryContextAnalysisOutputValidationError("repositoryContext must be an object")

        evidence_references = set()
        evidence = repo_context.get("evidence", [])
        if isinstance(evidence, list):
            for item in evidence:
                if not isinstance(item, dict):
                    continue
                ref = item.get("reference")
                if isinstance(ref, str):
                    evidence_references.add(ref)
                related = item.get("relatedReferences", [])
                if isinstance(related, list):
                    for r in related:
                        if isinstance(r, str):
                            evidence_references.add(r)

        all_findings = (
            output.architecture_findings
            + output.decision_findings
            + output.evidence_findings
            + output.historical_context
            + output.constraint_findings
            + output.impacted_component_findings
        )

        for finding in all_findings:
            finding_refs = {er.reference for er in finding.grounding.evidence_references}
            unknown = finding_refs - evidence_references
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
                    raise StoryContextAnalysisOutputValidationError(
                        f"Finding {finding.title} must have relationType"
                    )
                if finding.grounding.relation_type.value not in VALID_RELATION_TYPES:
                    raise StoryContextAnalysisOutputValidationError(
                        f"Finding {finding.title} has invalid relationType: {finding.grounding.relation_type.value}"
                    )

        for unc in output.uncertainties:
            for er in unc.related_evidence:
                if er.reference not in evidence_references:
                    raise StoryContextAnalysisOutputValidationError(
                        f"Uncertainty references unknown evidence: {er.reference}"
                    )

        if output.confidence.level not in {"HIGH", "MEDIUM", "LOW"}:
            raise StoryContextAnalysisOutputValidationError(
                f"Invalid confidence level: {output.confidence.level}"
            )

        for cls in output.output_classification:
            if cls.classification not in {"FACTUAL_EXTRACTION", "AI_INTERPRETATION", "RECOMMENDATION"}:
                raise StoryContextAnalysisOutputValidationError(
                    f"Invalid output classification: {cls.classification}"
                )

    async def _send_failure(
        self,
        submission: AiTaskSubmissionRequest,
        external_job_id: UUID,
        error_code: str,
        error: Exception,
        prompt: Prompt | None = None,
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