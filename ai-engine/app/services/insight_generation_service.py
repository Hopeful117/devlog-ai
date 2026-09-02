from datetime import datetime, timezone
import logging
import re
from uuid import UUID

from pydantic import ValidationError

from app.clients.core_callback_client import CoreCallbackClient
from app.models.proposal import AiTaskResultStatus, ProposalType
from app.models.intent import InsightType
from app.prompts.insight import (
    ArchitectureKnowledgeRetryCandidate,
    InsightPromptBuilder,
    RelationshipRetryContext,
    UncoveredRelationshipRetryItem,
)
from app.providers.base import LlmProvider, Prompt
from app.schemas.ai_task import AiTaskSubmissionRequest
from app.schemas.ai_task_result import (
    AiProposalResult,
    AiTaskResultError,
    AiTaskResultRequest,
    AnalysisSynthesisResult,
    PromptExecutionMetadata,
    SynthesisSectionResult,
)
from app.schemas.insight import InsightGenerationOutput


class InsightOutputValidationError(ValueError):
    def __init__(
        self,
        message: str,
        *,
        relationship_context: RelationshipRetryContext | None = None,
    ) -> None:
        super().__init__(message)
        self.relationship_context = relationship_context


logger = logging.getLogger(__name__)


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
            prompt = self._prompt_builder.build(submission)
        except ValueError as intent_error:
            await self._send_failure(
                submission, external_job_id,
                getattr(intent_error, "code", "PROMPT_CONSTRUCTION_FAILED"),
                intent_error,
            )
            return
        require_synthesis = (
            submission.intent.id == "architecture-overview"
            and submission.intent.version == "v2"
        )
        try:
            output = await self._generate_and_validate(
                prompt, submission.selected_knowledge, set(submission.intent.supported_insight_types),
                require_synthesis=require_synthesis,
            )
        except (ValidationError, InsightOutputValidationError, ValueError) as error:
            relationship_context = (
                error.relationship_context
                if isinstance(error, InsightOutputValidationError)
                else None
            )
            corrective_prompt = self._prompt_builder.corrective_retry(
                prompt, error, relationship_context
            )
            try:
                output = await self._generate_and_validate(
                    corrective_prompt,
                    submission.selected_knowledge,
                    set(submission.intent.supported_insight_types),
                    require_synthesis=require_synthesis,
                )
                prompt = corrective_prompt
            except (ValidationError, InsightOutputValidationError, ValueError) as retry_error:
                await self._send_failure(
                    submission,
                    external_job_id,
                    "INVALID_LLM_OUTPUT",
                    retry_error,
                    corrective_prompt,
                )
                return
            except Exception as provider_error:
                await self._send_failure(
                    submission,
                    external_job_id,
                    "LLM_PROVIDER_ERROR",
                    provider_error,
                    corrective_prompt,
                )
                return
        except Exception as provider_error:
            await self._send_failure(
                submission,
                external_job_id,
                "LLM_PROVIDER_ERROR",
                provider_error,
                prompt,
            )
            return

        proposals = [
            AiProposalResult(
                type=ProposalType.INSIGHT,
                payload=self._payload(submission, proposal),
                confidence=proposal.confidence,
                supporting_fact_ids=proposal.supporting_fact_ids,
                supporting_observation_ids=proposal.supporting_observation_ids,
                evidence_references=proposal.evidence_references,
            )
            for proposal in output.proposals
        ]
        synthesis = None
        if output.synthesis is not None:
            synthesis = AnalysisSynthesisResult(
                title=output.synthesis.title,
                sections=[
                    SynthesisSectionResult(name=s.name, content=s.content)
                    for s in output.synthesis.sections
                ],
                delta_conclusion=output.synthesis.delta_conclusion,
                grounding_references=output.synthesis.grounding_references,
            )
        logger.info(
            "Prompt execution completed promptVersion=%s promptDigest=%s provider=%s model=%s userMessageSize=%d hasSynthesis=%s",
            prompt.prompt_version, prompt.content_digest, self._provider.provider_name,
            self._provider.model_identifier, len(prompt.user_message), synthesis is not None,
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
                synthesis=synthesis,
            ),
        )

    async def _generate_and_validate(
        self,
        prompt: Prompt,
        context: dict[str, object],
        supported_insight_types: set[InsightType],
        *,
        require_synthesis: bool = False,
    ) -> InsightGenerationOutput:
        output = await self._provider.generate_structured(
            prompt,
            InsightGenerationOutput,
        )
        validated = InsightGenerationOutput.model_validate(output)
        self._validate_output(validated, context, supported_insight_types, require_synthesis=require_synthesis)
        return validated

    def _validate_output(
        self,
        output: InsightGenerationOutput,
        context: dict[str, object],
        supported_insight_types: set[InsightType],
        *,
        require_synthesis: bool = False,
    ) -> None:
        facts = context.get("selectedFacts", [])
        observations = context.get("selectedObservations", [])
        repository_context = context.get("repositoryContext", {})
        if not isinstance(facts, list) or not isinstance(observations, list):
            raise InsightOutputValidationError(
                "SelectedKnowledge facts and observations must be arrays"
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
        if isinstance(repository_context, dict):
            repository_evidence = repository_context.get("evidence", [])
            if isinstance(repository_evidence, list):
                for item in repository_evidence:
                    if not isinstance(item, dict):
                        continue
                    reference = item.get("reference")
                    if isinstance(reference, str):
                        evidence_references.add(reference)
                    related = item.get("relatedReferences", [])
                    if isinstance(related, list):
                        evidence_references.update(
                            value for value in related if isinstance(value, str)
                        )

        if output.synthesis is not None:
            if not output.synthesis.title or not output.synthesis.title.strip():
                raise InsightOutputValidationError("Synthesis title must not be blank")
            if not output.synthesis.sections:
                raise InsightOutputValidationError("Synthesis must have at least one section")
            self._require_subset(
                set(output.synthesis.grounding_references),
                evidence_references
                | {str(identifier) for identifier in fact_ids}
                | {str(identifier) for identifier in observation_ids}
                | self._collect_selected_identifiers(context),
                "synthesis.groundingReferences",
            )
        if require_synthesis and output.synthesis is None:
            raise InsightOutputValidationError(
                "Architecture Overview v2 requires a synthesis object"
            )
        if not require_synthesis and output.synthesis is not None:
            raise InsightOutputValidationError(
                "Synthesis is not allowed for this Intent version"
            )
        if output.synthesis is not None:
            has_deltas = bool(output.proposals)
            if has_deltas != (
                output.synthesis.delta_conclusion.value == "DELTAS_PROPOSED"
            ):
                raise InsightOutputValidationError(
                    "deltaConclusion must match whether proposals are present"
                )
            if require_synthesis and not has_deltas:
                relationship_context = self._relationship_retry_context(context)
                if relationship_context is not None:
                    raise InsightOutputValidationError(
                        "An explicit selected component relationship absent from existing "
                        "architecture knowledge requires a grounded architecture delta proposal",
                        relationship_context=relationship_context,
                    )

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
            if proposal.delta_type.value == "ENRICHES":
                existing = context.get("existingArchitectureKnowledge", [])
                allowed_targets = {
                    UUID(str(item["insightId"]))
                    for item in existing
                    if isinstance(item, dict) and "insightId" in item
                } if isinstance(existing, list) else set()
                if proposal.target_insight_id not in allowed_targets:
                    raise InsightOutputValidationError(
                        "targetInsightId must exist in existingArchitectureKnowledge"
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

    def _collect_selected_identifiers(self, value: object) -> set[str]:
        identifiers: set[str] = set()
        if isinstance(value, dict):
            for key, nested in value.items():
                if key in {"id", "insightId"} and isinstance(nested, str):
                    try:
                        identifiers.add(str(UUID(nested)))
                    except ValueError:
                        pass
                identifiers.update(self._collect_selected_identifiers(nested))
        elif isinstance(value, list):
            for nested in value:
                identifiers.update(self._collect_selected_identifiers(nested))
        return identifiers

    _RELATIONSHIP_KEY_VALUE_PATTERN = re.compile(
        r"(?:^|,)\s*from=([^,\s]+)\s*,\s*to=([^,\s]+)(?:,|$)",
        re.IGNORECASE,
    )
    _RELATIONSHIP_ARROW_PATTERN = re.compile(
        r"(?<![A-Za-z0-9_.-])([A-Za-z0-9_.-]+)\s*(?:->|→)\s*"
        r"([A-Za-z0-9_.-]+)(?![A-Za-z0-9_.-])"
    )
    _RELATIONSHIP_KNOWLEDGE_FIELDS = ("title", "content", "summary", "rationale")

    def _has_uncovered_relationship(self, context: dict[str, object]) -> bool:
        return bool(self._uncovered_relationships(context))

    def _relationship_retry_context(
        self, context: dict[str, object]
    ) -> RelationshipRetryContext | None:
        relationships = self._uncovered_relationships(context)
        if not relationships:
            return None
        candidates = self._relationship_retry_candidates(context, relationships)
        return RelationshipRetryContext(tuple(relationships), tuple(candidates))

    def _uncovered_relationships(
        self, context: dict[str, object]
    ) -> list[UncoveredRelationshipRetryItem]:
        existing_relationships = self._existing_relationships(
            context.get("existingArchitectureKnowledge", [])
        )
        facts = context.get("selectedFacts", [])
        if not isinstance(facts, list):
            return []
        uncovered: list[UncoveredRelationshipRetryItem] = []
        seen: set[tuple[str, str, str]] = set()
        for fact in facts:
            if not isinstance(fact, dict):
                continue
            content = fact.get("content")
            if not isinstance(content, str):
                continue
            relationship = self._selected_relationship(content)
            if relationship is None or relationship in existing_relationships:
                continue
            relationship_type = fact.get("type")
            normalized_type = (
                relationship_type
                if isinstance(relationship_type, str) and relationship_type.strip()
                else "UNSPECIFIED_RELATIONSHIP"
            )
            key = (normalized_type, relationship[0], relationship[1])
            if key in seen:
                continue
            seen.add(key)
            references = fact.get("evidenceReferences", [])
            uncovered.append(UncoveredRelationshipRetryItem(
                normalized_type,
                relationship[0],
                relationship[1],
                tuple(sorted({
                    reference
                    for reference in references
                    if isinstance(reference, str) and reference.strip()
                })) if isinstance(references, list) else (),
            ))
        return uncovered

    def _relationship_retry_candidates(
        self,
        context: dict[str, object],
        relationships: list[UncoveredRelationshipRetryItem],
    ) -> list[ArchitectureKnowledgeRetryCandidate]:
        existing = context.get("existingArchitectureKnowledge", [])
        if not isinstance(existing, list):
            return []
        relationship_references = {
            reference
            for relationship in relationships
            for reference in relationship.evidence_references
        }
        candidates: list[tuple[ArchitectureKnowledgeRetryCandidate, set[str]]] = []
        seen_ids: set[str] = set()
        for item in existing:
            if not isinstance(item, dict):
                continue
            insight_id = item.get("insightId")
            title = item.get("title")
            content = item.get("content", item.get("summary"))
            if not all(isinstance(value, str) and value.strip()
                       for value in (insight_id, title, content)):
                continue
            normalized_id = str(insight_id)
            if normalized_id in seen_ids:
                continue
            seen_ids.add(normalized_id)
            references = item.get("evidenceReferences", [])
            candidate_references = {
                reference
                for reference in references
                if isinstance(reference, str) and reference.strip()
            } if isinstance(references, list) else set()
            candidates.append((ArchitectureKnowledgeRetryCandidate(
                normalized_id, str(title), str(content)
            ), candidate_references))
        matched = [
            candidate
            for candidate, references in candidates
            if relationship_references and relationship_references & references
        ]
        return matched or [candidate for candidate, _ in candidates]

    def _selected_relationship(self, content: str) -> tuple[str, str] | None:
        match = self._RELATIONSHIP_KEY_VALUE_PATTERN.search(content)
        return self._normalized_relationship(match) if match is not None else None

    def _existing_relationships(self, value: object) -> set[tuple[str, str]]:
        relationships: set[tuple[str, str]] = set()
        if not isinstance(value, list):
            return relationships
        for item in value:
            if not isinstance(item, dict):
                continue
            for field in self._RELATIONSHIP_KNOWLEDGE_FIELDS:
                text = item.get(field)
                if not isinstance(text, str):
                    continue
                relationships.update(
                    self._normalized_relationship(match)
                    for match in self._RELATIONSHIP_KEY_VALUE_PATTERN.finditer(text)
                )
                relationships.update(
                    self._normalized_relationship(match)
                    for match in self._RELATIONSHIP_ARROW_PATTERN.finditer(text)
                )
        return relationships

    def _normalized_relationship(self, match: re.Match[str]) -> tuple[str, str]:
        return match.group(1).lower(), match.group(2).lower()

    def _payload(self, submission: AiTaskSubmissionRequest, proposal: object) -> dict[str, object]:
        payload = {
            "insightType": proposal.insight_type.value,
            "title": proposal.title,
            "summary": proposal.summary,
            "rationale": proposal.rationale,
        }
        if submission.intent.id == "architecture-overview":
            payload["deltaType"] = proposal.delta_type.value
            if proposal.target_insight_id is not None:
                payload["targetInsightId"] = str(proposal.target_insight_id)
        return payload

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
