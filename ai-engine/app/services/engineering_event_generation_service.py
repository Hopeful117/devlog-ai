from datetime import datetime, timezone
from uuid import UUID
from pydantic import ValidationError
from app.clients.core_callback_client import CoreCallbackClient
from app.models.proposal import AiTaskResultStatus, ProposalType
from app.prompts.engineering_event import EngineeringEventPromptBuilder
from app.providers.base import LlmProvider, Prompt
from app.schemas.ai_task import AiTaskSubmissionRequest
from app.schemas.ai_task_result import AiProposalResult, AiTaskResultError, AiTaskResultRequest, PromptExecutionMetadata
from app.schemas.engineering_event import EngineeringEventGenerationOutput


class EngineeringEventOutputError(ValueError): pass


class EngineeringEventGenerationService:
    def __init__(self, provider: LlmProvider, prompt_builder: EngineeringEventPromptBuilder,
                 callback_client: CoreCallbackClient):
        self.provider, self.builder, self.callback = provider, prompt_builder, callback_client

    async def process(self, submission: AiTaskSubmissionRequest, external_job_id: UUID) -> None:
        prompt = None
        try:
            prompt = self.builder.build(submission)
            output = await self._generate(prompt, submission.selected_knowledge)
        except (ValidationError, EngineeringEventOutputError, ValueError) as first:
            if prompt is None:
                await self._failure(submission, external_job_id, "PROMPT_CONSTRUCTION_FAILED", first, None)
                return
            retry = self.builder.corrective_retry(prompt, first)
            try:
                output = await self._generate(retry, submission.selected_knowledge)
                prompt = retry
            except (ValidationError, EngineeringEventOutputError, ValueError) as second:
                await self._failure(submission, external_job_id, "INVALID_LLM_OUTPUT", second, retry)
                return
            except Exception as provider_failure:
                await self._failure(submission, external_job_id, "LLM_PROVIDER_ERROR",
                                    provider_failure, retry)
                return
        except Exception as error:
            await self._failure(submission, external_job_id, "LLM_PROVIDER_ERROR", error, prompt)
            return
        proposals = [AiProposalResult(type=ProposalType.ENGINEERING_EVENT,
            payload={"schemaVersion": p.schema_version, "category": p.category.value,
                "title": p.title, "summary": p.summary, "significance": p.significance},
            confidence=p.confidence, supporting_fact_ids=p.supporting_fact_ids,
            supporting_observation_ids=p.supporting_observation_ids,
            evidence_references=p.evidence_references) for p in output.proposals]
        await self.callback.send_result(submission.correlation_id,
            AiTaskResultRequest(correlation_id=submission.correlation_id,
                external_job_id=str(external_job_id), status=AiTaskResultStatus.COMPLETED,
                completed_at=datetime.now(timezone.utc), proposals=proposals, error=None,
                prompt_execution=self._metadata(prompt)))

    async def _generate(self, prompt: Prompt, context: dict[str, object]):
        output = EngineeringEventGenerationOutput.model_validate(
            await self.provider.generate_structured(prompt, EngineeringEventGenerationOutput))
        allowed = self.builder._grounding(context)
        fact_ids, observation_ids, references = (set(allowed["allowedSupportingFactIds"]),
            set(allowed["allowedSupportingObservationIds"]), set(allowed["allowedEvidenceReferences"]))
        seen = set()
        for proposal in output.proposals:
            key = (proposal.category.value, proposal.title.casefold())
            if key in seen:
                raise EngineeringEventOutputError("Duplicate Engineering Event proposal")
            seen.add(key)
            self._require_subset({str(v) for v in proposal.supporting_fact_ids},
                                 fact_ids, "supportingFactIds")
            self._require_subset({str(v) for v in proposal.supporting_observation_ids},
                                 observation_ids, "supportingObservationIds")
            unknown_references = set(proposal.evidence_references) - references
            if unknown_references:
                raise EngineeringEventOutputError(
                    f"evidenceReferences contains references absent from the allowed list: "
                    f"{sorted(unknown_references)}")
        return output

    @staticmethod
    def _require_subset(referenced: set[str], available: set[str], field_name: str) -> None:
        unknown = referenced - available
        if unknown:
            raise EngineeringEventOutputError(
                f"{field_name} contains identifiers absent from the corresponding allowed list: "
                f"{sorted(unknown)}")

    async def _failure(self, submission, job_id, code, error, prompt):
        await self.callback.send_result(submission.correlation_id,
            AiTaskResultRequest(correlation_id=submission.correlation_id, external_job_id=str(job_id),
                status=AiTaskResultStatus.FAILED, completed_at=datetime.now(timezone.utc), proposals=[],
                error=AiTaskResultError(code=code, message=(str(error) or code)[:5000]),
                prompt_execution=self._metadata(prompt) if prompt else None))

    def _metadata(self, prompt: Prompt):
        return PromptExecutionMetadata(prompt_version=prompt.prompt_version,
            provider=self.provider.provider_name, model_identifier=self.provider.model_identifier,
            prompt_content_digest=prompt.content_digest, context_digest=prompt.traceability.context_digest)
