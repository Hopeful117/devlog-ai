from datetime import datetime, timezone
import logging
from uuid import UUID

from pydantic import ValidationError

from app.clients.core_callback_client import CoreCallbackClient
from app.models.proposal import AiTaskResultStatus, ProposalType
from app.providers.base import LlmProvider, Prompt
from app.schemas.ai_task import AiTaskSubmissionRequest
from app.schemas.ai_task_result import (
    AiProposalResult,
    AiTaskResultError,
    AiTaskResultRequest,
    PromptExecutionMetadata,
)
from app.schemas.decision import EngineeringDecisionGenerationOutput
from app.services.interaction_trace import InteractionTraceCollector


logger = logging.getLogger(__name__)


class EngineeringDecisionGenerationService:
    def __init__(
        self,
        provider: LlmProvider,
        prompt_builder,
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
            output = await self._generate(prompt, traces)
        except (ValidationError, ValueError) as error:
            corrective_prompt = self._prompt_builder.corrective_retry(prompt, error)
            traces.retry(error)
            try:
                output = await self._generate(corrective_prompt, traces)
                prompt = corrective_prompt
            except (ValidationError, ValueError) as retry_error:
                await self._send_failure(
                    submission,
                    external_job_id,
                    "INVALID_LLM_OUTPUT",
                    retry_error,
                    corrective_prompt,
                    traces,
                )
                return
            except Exception as provider_error:
                await self._send_failure(
                    submission,
                    external_job_id,
                    "LLM_PROVIDER_ERROR",
                    provider_error,
                    corrective_prompt,
                    traces,
                )
                return
        except Exception as provider_error:
            await self._send_failure(
                submission,
                external_job_id,
                "LLM_PROVIDER_ERROR",
                provider_error,
                prompt,
                traces,
            )
            return

        proposals = [
            AiProposalResult(
                type=ProposalType.ENGINEERING_DECISION,
                payload=self._payload(proposal),
                confidence=1.0,
                supporting_fact_ids=[],
                supporting_observation_ids=[],
                evidence_references=[],
            )
            for proposal in output.proposals
        ]
        logger.info(
            "Decision prompt execution completed promptVersion=%s provider=%s model=%s",
            prompt.prompt_version,
            self._provider.provider_name,
            self._provider.model_identifier,
        )
        await self._callback_client.send_result(
            submission.correlation_id,
            AiTaskResultRequest(
                correlation_id=submission.correlation_id,
                external_job_id=str(external_job_id),
                status=AiTaskResultStatus.COMPLETED,
                completed_at=datetime.now(timezone.utc),
                proposals=proposals,
                error=None,
                prompt_execution=PromptExecutionMetadata(
                    prompt_version=prompt.prompt_version,
                    provider=self._provider.provider_name,
                    model_identifier=self._provider.model_identifier,
                    prompt_content_digest=prompt.content_digest,
                    context_digest=prompt.traceability.context_digest,
                ),
                interaction_traces=traces.traces,
            ),
        )

    async def _generate(
        self, prompt: Prompt, traces: InteractionTraceCollector
    ) -> EngineeringDecisionGenerationOutput:
        return await traces.generate_and_validate(
            prompt, EngineeringDecisionGenerationOutput, lambda output: None
        )

    def _payload(self, proposal: object) -> dict[str, object]:
        payload = {
            "title": proposal.title,
            "context": proposal.context,
            "choice": proposal.choice,
            "rationale": proposal.rationale,
        }
        if proposal.consequences is not None:
            payload["consequences"] = proposal.consequences
        return payload

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
                    message=str(error)[:5000] or error_code,
                ),
                prompt_execution=(
                    PromptExecutionMetadata(
                        prompt_version=prompt.prompt_version,
                        provider=self._provider.provider_name,
                        model_identifier=self._provider.model_identifier,
                        prompt_content_digest=prompt.content_digest,
                        context_digest=prompt.traceability.context_digest,
                    )
                    if prompt else None
                ),
                interaction_traces=traces.traces if traces else [],
            ),
        )
