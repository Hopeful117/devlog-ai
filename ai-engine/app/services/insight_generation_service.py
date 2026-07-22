from datetime import datetime, timezone
from uuid import UUID

from pydantic import ValidationError

from app.clients.core_callback_client import CoreCallbackClient
from app.models.proposal import AiTaskResultStatus, ProposalType
from app.models.intent import InsightType
from app.prompts.insight import InsightPromptBuilder
from app.providers.base import LlmProvider, LlmRequest
from app.schemas.ai_task import AiTaskSubmissionRequest
from app.schemas.ai_task_result import (
    AiProposalResult,
    AiTaskResultError,
    AiTaskResultRequest,
)
from app.schemas.insight import InsightGenerationOutput


class InsightOutputValidationError(ValueError):
    pass


class InsightGenerationService:
    def __init__(
        self,
        provider: LlmProvider,
        prompt_builder: InsightPromptBuilder,
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
            prompt = self._prompt_builder.build(submission.intent, submission.context)
        except ValueError as intent_error:
            await self._send_failure(
                submission, external_job_id, "INVALID_INTENT", intent_error
            )
            return
        try:
            output = await self._generate_and_validate(
                prompt, submission.context, set(submission.intent.supported_insight_types)
            )
        except (ValidationError, InsightOutputValidationError, ValueError) as error:
            corrective_prompt = self._prompt_builder.corrective_retry(prompt, error)
            try:
                output = await self._generate_and_validate(
                    corrective_prompt,
                    submission.context,
                    set(submission.intent.supported_insight_types),
                )
            except (ValidationError, InsightOutputValidationError, ValueError) as retry_error:
                await self._send_failure(
                    submission,
                    external_job_id,
                    "INVALID_LLM_OUTPUT",
                    retry_error,
                )
                return
            except Exception as provider_error:
                await self._send_failure(
                    submission,
                    external_job_id,
                    "LLM_PROVIDER_ERROR",
                    provider_error,
                )
                return
        except Exception as provider_error:
            await self._send_failure(
                submission,
                external_job_id,
                "LLM_PROVIDER_ERROR",
                provider_error,
            )
            return

        proposals = [
            AiProposalResult(
                type=ProposalType.INSIGHT,
                payload={
                    "insightType": proposal.insight_type.value,
                    "title": proposal.title,
                    "summary": proposal.summary,
                    "rationale": proposal.rationale,
                },
                confidence=proposal.confidence,
                supporting_fact_ids=proposal.supporting_fact_ids,
                supporting_observation_ids=proposal.supporting_observation_ids,
                evidence_references=proposal.evidence_references,
            )
            for proposal in output.proposals
        ]
        await self._callback_client.send_result(
            submission.correlation_id,
            AiTaskResultRequest(
                correlation_id=submission.correlation_id,
                external_job_id=str(external_job_id),
                status=AiTaskResultStatus.COMPLETED,
                completed_at=datetime.now(timezone.utc),
                proposals=proposals,
                error=None,
            ),
        )

    async def _generate_and_validate(
        self,
        prompt: LlmRequest,
        context: dict[str, object],
        supported_insight_types: set[InsightType],
    ) -> InsightGenerationOutput:
        output = await self._provider.generate_structured(
            prompt,
            InsightGenerationOutput,
        )
        validated = InsightGenerationOutput.model_validate(output)
        self._validate_output(validated, context, supported_insight_types)
        return validated

    def _validate_output(
        self,
        output: InsightGenerationOutput,
        context: dict[str, object],
        supported_insight_types: set[InsightType],
    ) -> None:
        facts = context.get("facts", [])
        observations = context.get("observations", [])
        if not isinstance(facts, list) or not isinstance(observations, list):
            raise InsightOutputValidationError(
                "AnalysisContext facts and observations must be arrays"
            )

        fact_ids = self._collect_ids(facts)
        observation_ids = self._collect_ids(observations)
        evidence_references = {
            reference
            for fact in facts
            if isinstance(fact, dict)
            for reference in fact.get("evidenceReferences", [])
            if isinstance(reference, str)
        }

        for proposal in output.proposals:
            if proposal.insight_type not in supported_insight_types:
                raise InsightOutputValidationError(
                    f"insightType {proposal.insight_type.value} is not supported by Intent"
                )
            self._require_subset(
                set(proposal.supporting_fact_ids),
                fact_ids,
                "supportingFactIds",
            )
            self._require_subset(
                set(proposal.supporting_observation_ids),
                observation_ids,
                "supportingObservationIds",
            )
            self._require_subset(
                set(proposal.evidence_references),
                evidence_references,
                "evidenceReferences",
            )

    def _collect_ids(self, items: list[object]) -> set[UUID]:
        identifiers: set[UUID] = set()
        for item in items:
            if not isinstance(item, dict) or "id" not in item:
                continue
            try:
                identifiers.add(UUID(str(item["id"])))
            except ValueError as error:
                raise InsightOutputValidationError(
                    "AnalysisContext contains an invalid identifier"
                ) from error
        return identifiers

    def _require_subset(
        self,
        referenced: set[object],
        available: set[object],
        field_name: str,
    ) -> None:
        unknown = referenced - available
        if unknown:
            raise InsightOutputValidationError(
                f"{field_name} contains references absent from AnalysisContext: "
                f"{sorted(str(value) for value in unknown)}"
            )

    async def _send_failure(
        self,
        submission: AiTaskSubmissionRequest,
        external_job_id: UUID,
        error_code: str,
        error: Exception,
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
            ),
        )
