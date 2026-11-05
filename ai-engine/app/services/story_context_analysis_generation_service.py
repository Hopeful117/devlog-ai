from datetime import datetime, timezone
import hashlib
import json
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
    ProviderStoryContextAnalysisResult,
    provider_result_to_internal,
    StoryAgentFollowUpResult,
)
from app.schemas.story_context_investigation import (
    INVESTIGATION_PROTOCOL_VERSION,
    InvestigationCapabilityResult,
    InvestigationActionDecision,
    InvestigationDecisionResponse,
    InvestigationFinalDecision,
    InvestigationPlanDecision,
)
from app.services.interaction_trace import InteractionTraceCollector


class StoryContextAnalysisOutputValidationError(ValueError):
    failure_category = "STRUCTURAL_CONTRACT_ERROR"
    failure_code = "STRUCTURAL_CONTRACT_ERROR"
    retryable = True


class StoryContextAnalysisGroundingError(StoryContextAnalysisOutputValidationError):
    failure_category = "GROUNDING_ERROR"
    failure_code = "GROUNDING_OTHER"


class StoryContextAnalysisDeterministicGroundingError(StoryContextAnalysisGroundingError):
    retryable = False


class StoryContextAnalysisUnknownReferenceError(StoryContextAnalysisGroundingError):
    failure_code = "GROUNDING_UNKNOWN_REFERENCE"


class StoryContextAnalysisRequestedFileScopeError(StoryContextAnalysisGroundingError):
    failure_code = "GROUNDING_REQUESTED_FILE_SCOPE"


class StoryContextAnalysisRelationTypeError(StoryContextAnalysisGroundingError):
    failure_code = "GROUNDING_RELATION_TYPE"


class StoryContextAnalysisCausalSupportError(StoryContextAnalysisGroundingError):
    failure_code = "GROUNDING_CAUSAL_SUPPORT"


class StoryContextAnalysisMissingEvidenceError(StoryContextAnalysisGroundingError):
    failure_code = "GROUNDING_MISSING_EVIDENCE"


class StoryContextAnalysisContractGroundingError(StoryContextAnalysisDeterministicGroundingError):
    failure_code = "GROUNDING_CONTRACT_INVALID"


class StoryContextAnalysisMappingGroundingError(StoryContextAnalysisDeterministicGroundingError):
    failure_code = "GROUNDING_MAPPING_INVALID"


class StoryContextAnalysisSemanticSupportError(StoryContextAnalysisOutputValidationError):
    failure_category = "SEMANTIC_SUPPORT_ERROR"
    failure_code = "SEMANTIC_SUPPORT_ERROR"


# Findings that express relationships and must have relationType
RELATIONSHIP_BEARING_FINDING_TYPES = {
    "architecture_findings",
    "decision_findings",
    "historical_context",
    "impacted_component_findings",
}

VALID_RELATION_TYPES = {rt.value for rt in RelationType}


logger = logging.getLogger(__name__)


class _InvestigationTerminal(ValueError):
    retryable = False

    def __init__(self, code: str) -> None:
        self.code = code
        super().__init__(code)


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

    @property
    def provider(self) -> LlmProvider:
        return self._provider

    async def process(
        self,
        submission: AiTaskSubmissionRequest,
        external_job_id: UUID,
    ) -> None:
        if self._investigation_enabled(submission):
            await self._process_investigation(submission, external_job_id)
            return
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
            if not getattr(error, "retryable", True):
                await self._send_failure(
                    submission, external_job_id, self._callback_error_code(error), error, prompt, traces
                )
                return
            corrective_prompt = self._prompt_builder.corrective_retry(prompt, error)
            traces.retry(error)
            try:
                output = await self._generate_and_validate(
                    corrective_prompt, submission.selected_knowledge, submission.grounding_contract or {}, traces
                )
                prompt = corrective_prompt
            except (ValidationError, StoryContextAnalysisOutputValidationError, ValueError) as retry_error:
                await self._send_failure(
                    submission, external_job_id, self._callback_error_code(retry_error), retry_error,
                    corrective_prompt, traces
                )
                return
            except Exception as provider_error:
                await self._send_failure(
                    submission, external_job_id, self._callback_error_code(provider_error, "LLM_PROVIDER_ERROR"),
                    provider_error, corrective_prompt, traces
                )
                return
        except Exception as provider_error:
            await self._send_failure(
                submission, external_job_id, self._callback_error_code(provider_error, "LLM_PROVIDER_ERROR"),
                provider_error, prompt, traces
            )
            return

        if isinstance(output, StoryContextAnalysisResult):
            output = self._apply_execution_provenance(output, prompt)

        logger.info(
            "Story context analysis generation completed promptVersion=%s promptDigest=%s provider=%s model=%s",
            prompt.prompt_version, prompt.content_digest, self._provider.provider_name,
            self._provider.model_identifier,
        )

        follow_up = self._is_follow_up(submission)
        await self._callback_client.send_result(
            submission.ai_task_id,
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
                    selection_digest=prompt.traceability.selection_digest,
                    projection_digest=prompt.traceability.projection_digest,
                    projection_version=prompt.traceability.projection_version,
                    scope=prompt.traceability.scope,
                    freshness=prompt.traceability.freshness,
                    grounding_digest=prompt.traceability.grounding_digest,
                    protocol_version="story-context-agent-protocol/v1",
                ),
                synthesis=None,
                analysis_result=None if follow_up else output,
                follow_up_result=output if follow_up else None,
                interaction_traces=traces.traces,
            ),
        )

    async def _process_investigation(
        self, submission: AiTaskSubmissionRequest, external_job_id: UUID
    ) -> None:
        traces = InteractionTraceCollector(self._provider, submission)
        results: list[dict[str, object]] = []
        actions_taken = 0
        budget = submission.metadata["investigation"].get("budget", {})
        maximum_steps = budget.get("maximumSteps", 0)
        prompt: Prompt | None = None
        try:
            prompt = self._prompt_builder.build_investigation(submission)
            decision = await self._investigation_decision(prompt, traces)
            if not isinstance(decision, InvestigationPlanDecision):
                raise ValueError("Investigation must begin with a PLAN decision")
            pending = list(decision.plan.actions)
            if not pending:
                raise ValueError("Investigation plan must contain an action")
            while True:
                if isinstance(decision, InvestigationFinalDecision):
                    await self._complete_investigation(
                        submission, external_job_id, prompt, traces, decision.stop_reason, decision.result
                    )
                    return
                if actions_taken >= maximum_steps:
                    raise _InvestigationTerminal("BUDGET_EXHAUSTED")
                if isinstance(decision, InvestigationPlanDecision):
                    if not pending:
                        raise ValueError("Investigation plan did not contain a usable action")
                    action = pending.pop(0)
                elif isinstance(decision, InvestigationActionDecision):
                    action = decision.action

                response = await self._callback_client.execute_investigation_action(
                    submission.ai_task_id, submission.correlation_id,
                    self._action_payload(action, submission, traces)
                )
                action_accounting = self._action_payload(action, submission, traces)
                action_result = response.get("result")
                if not isinstance(action_result, dict):
                    raise _InvestigationTerminal("INVESTIGATION_RESULT_INVALID")
                try:
                    typed_result = InvestigationCapabilityResult.model_validate(action_result)
                except ValidationError as error:
                    raise _InvestigationTerminal("INVESTIGATION_RESULT_INVALID") from error
                self._validate_investigation_identity(submission, typed_result)
                account_generations = getattr(traces, "account_provider_generations", None)
                if account_generations is not None:
                    account_generations(action_accounting["providerGenerationCount"])
                # Keep the complete Core result, including evidence and grounding,
                # in the next prompt. Metadata-only action summaries are not enough.
                results.append(typed_result.model_dump(mode="json", by_alias=True, exclude_none=True))
                actions_taken += 1
                outcome = typed_result.outcome
                if outcome in {"REJECTED", "UNAVAILABLE"}:
                    raise _InvestigationTerminal(
                        "BUDGET_EXHAUSTED" if typed_result.rejection_code == "BUDGET_EXHAUSTED"
                        or typed_result.stop_reason == "BUDGET_EXHAUSTED"
                        else "INVESTIGATION_ACTION_REJECTED"
                    )
                self._synchronize_investigation_grounding(submission, response)
                prompt = self._prompt_builder.build_investigation(submission, results)
                decision = await self._investigation_decision(prompt, traces)
        except _InvestigationTerminal as error:
            await self._send_failure(
                submission, external_job_id, error.code, error, prompt, traces,
                self._investigation_stop_reason(error.code),
            )
        except Exception as error:
            await self._send_failure(
                submission, external_job_id, self._callback_error_code(error, "INVESTIGATION_FAILED"),
                error, prompt, traces,
                self._investigation_stop_reason(self._callback_error_code(error, "INVESTIGATION_FAILED")),
            )

    async def _investigation_decision(
        self, prompt: Prompt, traces: InteractionTraceCollector
    ):
        response = await traces.generate_and_validate(
            prompt, InvestigationDecisionResponse, lambda value: None
        )
        return response.root

    async def _complete_investigation(
        self, submission: AiTaskSubmissionRequest, external_job_id: UUID,
        prompt: Prompt | None, traces: InteractionTraceCollector,
        stop_reason,
        provider_result: ProviderStoryContextAnalysisResult,
    ) -> None:
        if prompt is None:
            raise ValueError("Investigation final prompt is missing")
        output = provider_result_to_internal(provider_result)
        self._validate_output(output, submission.selected_knowledge, submission.grounding_contract or {})
        output = self._apply_execution_provenance(output, prompt)
        await self._callback_client.send_result(
            submission.ai_task_id, submission.correlation_id,
            AiTaskResultRequest(
                correlation_id=submission.correlation_id,
                external_job_id=str(external_job_id), status=AiTaskResultStatus.COMPLETED,
                completed_at=datetime.now(timezone.utc), proposals=[], error=None,
                prompt_execution=self._execution_metadata(prompt), analysis_result=output,
                investigation_stop_reason=stop_reason,
                investigation_provider_generation_count=len(traces.traces),
                investigation_provider_elapsed_millis=sum(trace.duration_ms for trace in traces.traces),
                investigation_provider_output_tokens=self._provider_output_tokens(traces),
                interaction_traces=traces.traces,
            ),
        )

    @staticmethod
    def _action_payload(action: object, submission, traces) -> dict[str, object]:
        accounting_delta = getattr(traces, "provider_accounting_delta", None)
        if accounting_delta is None:
            generation_count, elapsed_millis, output_tokens = 1, 0, getattr(
                traces, "last_provider_output_tokens", 0
            )
        else:
            generation_count, elapsed_millis, output_tokens = accounting_delta()
        payload = action.model_dump(mode="json", by_alias=True, exclude_none=True)
        investigation = submission.metadata.get("investigation")
        if not isinstance(investigation, dict):
            raise _InvestigationTerminal("INVESTIGATION_STATE_INVALID")
        payload.update({
            "protocolVersion": INVESTIGATION_PROTOCOL_VERSION,
            "executionId": investigation.get("executionId"),
            "parentTaskId": str(submission.ai_task_id),
            "correlationId": str(submission.correlation_id),
            "provider": submission.provider,
            "credentialBindingId": str(submission.credential_binding_id),
            "providerGenerationCount": generation_count,
            "providerElapsedMillis": elapsed_millis,
            "providerOutputTokens": output_tokens,
        })
        kind = payload.pop("kind")
        payload["limits"] = {
            "resultLimit": payload.pop("resultLimit"),
            "maximumBytes": payload.pop("maximumBytes"),
            "maximumTokens": payload.pop("maximumTokens"),
        }
        if kind == "SEARCH_EVIDENCE":
            payload["kind"] = kind
            if "pathPrefix" in payload:
                payload["path"] = payload.pop("pathPrefix")
        else:
            payload["kind"] = kind
            payload["range"] = {
                "startLine": payload.pop("startLine"),
                "endLine": payload.pop("endLine"),
            }
        return payload

    @staticmethod
    def _validate_investigation_identity(submission, result: InvestigationCapabilityResult) -> None:
        investigation = submission.metadata.get("investigation")
        if not isinstance(investigation, dict) or result.protocol_version != INVESTIGATION_PROTOCOL_VERSION \
                or str(result.parent_task_id) != str(submission.ai_task_id) \
                or str(result.execution_id) != str(investigation.get("executionId")):
            raise _InvestigationTerminal("INVESTIGATION_IDENTITY_MISMATCH")

    @staticmethod
    def _investigation_enabled(submission: AiTaskSubmissionRequest) -> bool:
        value = submission.metadata.get("investigation")
        return isinstance(value, dict) and value.get("initialized") is True

    @staticmethod
    def _synchronize_investigation_grounding(
        submission: AiTaskSubmissionRequest, response: dict[str, object]
    ) -> None:
        result = response.get("result")
        if not isinstance(result, dict):
            raise _InvestigationTerminal("INVESTIGATION_RESULT_INVALID")
        contract = result.get("groundingContract")
        digest = result.get("groundingDigest")
        if not isinstance(contract, dict) or not isinstance(
            contract.get("allowedGroundingReferences"), list
        ) or not isinstance(digest, str) or len(digest) != 64:
            raise _InvestigationTerminal("GROUNDING_CONTRACT_INVALID")
        expected = hashlib.sha256(
            json.dumps(contract, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
        ).hexdigest()
        if digest != expected:
            raise _InvestigationTerminal("GROUNDING_CONTRACT_INVALID")
        submission.grounding_contract = contract
        submission.metadata["groundingDigest"] = digest

    def _apply_execution_provenance(
        self, output: StoryContextAnalysisResult, prompt: Prompt
    ) -> StoryContextAnalysisResult:
        payload = output.model_dump(mode="json", by_alias=True)
        payload["confidence"] = {
            "level": output.confidence.level,
            "rationale": output.confidence.rationale,
        }
        provenance = payload["provenance"]
        provenance.update({
            "contextDigest": prompt.traceability.context_digest,
            "promptVersion": prompt.prompt_version,
            "provider": self._provider.provider_name,
            "modelIdentifier": self._provider.model_identifier,
            "promptContentDigest": prompt.content_digest,
            "intentId": prompt.intent_id,
            "intentVersion": prompt.intent_version,
        })
        return StoryContextAnalysisResult.model_validate(payload)

    async def _generate_and_validate(
        self,
        prompt: Prompt,
        context: dict[str, object],
        grounding_contract: dict[str, object],
        traces: InteractionTraceCollector,
    ) -> StoryContextAnalysisResult | StoryAgentFollowUpResult:
        if self._is_follow_up_from_prompt(prompt):
            provider_output = await traces.generate_and_validate(
                prompt,
                StoryAgentFollowUpResult,
                lambda output: self._validate_follow_up_output(output, grounding_contract),
            )
            return provider_output
        provider_output = await traces.generate_and_validate(
            prompt,
            ProviderStoryContextAnalysisResult,
            lambda output: self._validate_output(
                provider_result_to_internal(output), context, grounding_contract
            ),
        )
        return provider_result_to_internal(provider_output)

    def _is_follow_up(self, submission: AiTaskSubmissionRequest) -> bool:
        return isinstance(submission.metadata.get("parentSnapshotId"), str)

    def _is_follow_up_from_prompt(self, prompt: Prompt) -> bool:
        return "FOLLOW-UP QUESTION" in getattr(prompt, "user_message", "")

    def _validate_follow_up_output(
        self, output: StoryAgentFollowUpResult, grounding_contract: dict[str, object]
    ) -> None:
        allowed_refs = self._allowed_grounding_refs(grounding_contract)
        references = output.evidence_references + output.next_step.evidence_references
        for uncertainty in output.uncertainties:
            references.extend(getattr(uncertainty, "related_evidence", []))
        unknown = {reference.reference for reference in references} - allowed_refs
        if unknown:
            raise StoryContextAnalysisUnknownReferenceError(
                f"Follow-up references unknown evidence: {sorted(unknown)}"
            )
    def _allowed_grounding_refs(self, grounding_contract: dict[str, object]) -> set[str]:
        typed_list = grounding_contract.get("allowedGroundingReferences")
        if grounding_contract.get("groundingContractVersion") != "story-context-grounding/v1" or not isinstance(typed_list, list):
            raise StoryContextAnalysisContractGroundingError("typed grounding allow-list is required")
        allowed_refs: set[str] = set()
        for entry in typed_list:
            if not isinstance(entry, dict) or entry.get("type") != "REPOSITORY_EVIDENCE":
                raise StoryContextAnalysisContractGroundingError("Invalid typed repository grounding reference")
            if entry.get("scope") != "PROJECT_REVISION" or not entry.get("project") or not entry.get("revision"):
                raise StoryContextAnalysisContractGroundingError("Repository grounding scope is incomplete")
            reference = entry.get("ref")
            if not isinstance(reference, str) or not reference:
                raise StoryContextAnalysisMappingGroundingError("Repository grounding reference is missing")
            if reference != entry.get("coreReference") or reference != entry.get("taskReference"):
                raise StoryContextAnalysisMappingGroundingError("Repository grounding mapping is inconsistent")
            allowed_refs.add(reference)
        return allowed_refs

    def _validate_output(
        self,
        output: StoryContextAnalysisResult,
        context: dict[str, object],
        grounding_contract: dict[str, object],
    ) -> None:
        # Use Java-authored grounding contract (authoritative per Story 0112 D14)
        if grounding_contract.get("groundingContractVersion") != "story-context-grounding/v1":
            raise StoryContextAnalysisContractGroundingError("V1 requires the typed grounding contract")
        if "allowedEvidenceReferences" in grounding_contract:
            raise StoryContextAnalysisContractGroundingError("legacy grounding allow-list is forbidden in V1")
        allowed_refs = set()
        typed_list = grounding_contract.get("allowedGroundingReferences")
        if not isinstance(typed_list, list):
            raise StoryContextAnalysisContractGroundingError("typed grounding allow-list is required")
        for entry in typed_list:
            if not isinstance(entry, dict) or entry.get("type") != "REPOSITORY_EVIDENCE":
                raise StoryContextAnalysisContractGroundingError("Invalid typed repository grounding reference")
            scope = entry.get("scope")
            if scope != "PROJECT_REVISION":
                raise StoryContextAnalysisContractGroundingError("REPOSITORY scope is forbidden in Story Context V1")
            if not entry.get("project") or not entry.get("revision"):
                raise StoryContextAnalysisContractGroundingError("Repository grounding scope is incomplete")
            if entry.get("ref") != entry.get("coreReference") or entry.get("ref") != entry.get("taskReference"):
                raise StoryContextAnalysisMappingGroundingError("Repository grounding mapping is inconsistent")
            if isinstance(entry.get("ref"), str):
                allowed_refs.add(entry["ref"])

        self._validate_requested_file_grounding(output, context, typed_list)

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
                raise StoryContextAnalysisCausalSupportError(
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
                raise StoryContextAnalysisUnknownReferenceError(
                    f"Causal claim {claim.source} -> {claim.target} references unknown evidence: {sorted(unknown)}"
                )
            if any(er.role is None for er in claim.evidence_references):
                raise StoryContextAnalysisCausalSupportError(
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
                raise StoryContextAnalysisUnknownReferenceError(
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
            if (
                finding.grounding.classification in {"FACTUAL_EXTRACTION", "AI_INTERPRETATION"}
                and not finding.grounding.evidence_references
            ):
                raise StoryContextAnalysisMissingEvidenceError(
                    f"Finding {finding.title} requires at least one evidence reference"
                )
            # Validate relationType for relationship-bearing findings
            finding_type = type(finding).__name__.lower().replace("finding", "_findings").replace("historicalcontextitem", "historical_context")
            if finding_type in RELATIONSHIP_BEARING_FINDING_TYPES:
                if finding.grounding.relation_type is None:
                    raise StoryContextAnalysisRelationTypeError(
                        f"Finding {finding.title} must have relationType"
                    )
                if finding.grounding.relation_type.value not in VALID_RELATION_TYPES:
                    raise StoryContextAnalysisRelationTypeError(
                        f"Finding {finding.title} has invalid relationType: {finding.grounding.relation_type.value}"
                    )

        for unc in output.uncertainties:
            for er in unc.related_evidence:
                if er.reference not in allowed_refs:
                    raise StoryContextAnalysisUnknownReferenceError(
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

    def _validate_requested_file_grounding(
        self,
        output: StoryContextAnalysisResult,
        context: dict[str, object],
        typed_list: list[object],
    ) -> None:
        request = context.get("request") or context.get("scope")
        if not isinstance(request, dict):
            return
        requested_files = request.get("files")
        if not isinstance(requested_files, list) or not requested_files:
            return
        requested = {value for value in requested_files if isinstance(value, str) and value}
        if not requested:
            return

        file_grounded_refs = {
            entry.get("ref")
            for entry in typed_list
            if isinstance(entry, dict)
            and isinstance(entry.get("ref"), str)
            and any(
                entry["ref"] == file or entry["ref"].endswith(f":{file}")
                or (isinstance(entry.get("provenance"), dict)
                    and entry["provenance"].get("originatingFile") == file)
                for file in requested
            )
        }
        findings = (
            output.architecture_findings
            + output.decision_findings
            + output.evidence_findings
            + output.historical_context
            + output.constraint_findings
            + output.impacted_component_findings
        )
        for finding in findings:
            references = {
                value.reference for value in finding.grounding.evidence_references
            }
            if references and not references & file_grounded_refs:
                raise StoryContextAnalysisRequestedFileScopeError(
                    f"Finding {finding.title} does not cite evidence from requested file scope"
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
            raise StoryContextAnalysisUnknownReferenceError(
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
        investigation_stop_reason: str | None = None,
    ) -> None:
        await self._callback_client.send_result(
            submission.ai_task_id,
            submission.correlation_id,
            AiTaskResultRequest(
                correlation_id=submission.correlation_id,
                external_job_id=str(external_job_id),
                status=AiTaskResultStatus.FAILED,
                completed_at=datetime.now(timezone.utc),
                proposals=[],
                error=AiTaskResultError(
                    code=error_code,
                    message=self._callback_error_message(error),
                ),
                prompt_execution=(
                    self._execution_metadata(prompt)
                    if prompt is not None
                    else self._failure_execution_metadata(submission)
                ),
                investigation_stop_reason=investigation_stop_reason,
                investigation_provider_generation_count=len(traces.traces) if traces else None,
                investigation_provider_elapsed_millis=(
                    sum(trace.duration_ms for trace in traces.traces) if traces else None
                ),
                investigation_provider_output_tokens=self._provider_output_tokens(traces) if traces else None,
                interaction_traces=traces.traces if traces else [],
            ),
        )

    @staticmethod
    def _investigation_stop_reason(error_code: str) -> str:
        if error_code == "BUDGET_EXHAUSTED":
            return "BUDGET_EXHAUSTED"
        if error_code in {"INVESTIGATION_ACTION_REJECTED", "ACTION_REJECTED"}:
            return "ACTION_REJECTED"
        if error_code in {"PROVIDER_INCOMPLETE_RESPONSE", "PROVIDER_INCOMPLETE"}:
            return "PROVIDER_INCOMPLETE"
        return "VALIDATION_FAILED"

    @staticmethod
    def _provider_output_tokens(traces) -> int:
        value = getattr(traces, "provider_output_tokens", None)
        if isinstance(value, int):
            return value
        return sum(
            trace.output_tokens for trace in traces.traces
            if isinstance(trace.output_tokens, int) and trace.output_tokens >= 0
        )

    @staticmethod
    def _callback_error_code(error: Exception, fallback: str = "INVALID_LLM_OUTPUT") -> str:
        if getattr(error, "incomplete_reason", None):
            return "PROVIDER_INCOMPLETE_RESPONSE"
        failure_code = getattr(error, "failure_code", None)
        if isinstance(failure_code, str) and failure_code:
            return failure_code[:100]
        return fallback

    @staticmethod
    def _callback_error_message(error: Exception) -> str:
        if getattr(error, "incomplete_reason", None) or getattr(error, "failure_category", None) == "PROVIDER_INCOMPLETE":
            return "Provider response was incomplete; no retry was attempted."
        if getattr(error, "failure_category", None) == "GROUNDING_ERROR":
            return "Grounding validation failed; inspect the bounded interaction trace diagnostics."
        if getattr(error, "failure_category", None) == "STRUCTURAL_CONTRACT_ERROR":
            return "Structured output contract validation failed."
        return "LLM output validation failed."

    def _failure_execution_metadata(
        self, submission: AiTaskSubmissionRequest
    ) -> PromptExecutionMetadata:
        """Build a valid identity echo when prompt construction never completed.

        Core owns these identities and requires them on every Story Context
        callback, including failures. Provider/prompt fields are deliberately
        marked unavailable because no prompt was rendered.
        """
        metadata = submission.metadata
        context_digest = submission.context_digest or metadata.get("contextDigest")
        projection_digest = submission.projection_digest or metadata.get("projectionDigest")
        if not isinstance(context_digest, str) or not isinstance(projection_digest, str):
            raise ValueError("Prompt construction failure cannot preserve Core identities")
        selection_digest = submission.selection_digest or metadata.get("selectionDigest")
        return PromptExecutionMetadata(
            prompt_version=submission.intent.prompt_template,
            provider="unavailable",
            model_identifier="unavailable",
            prompt_content_digest=hashlib.sha256(
                f"prompt-construction-failure:{submission.correlation_id}".encode()
            ).hexdigest(),
            context_digest=context_digest,
            selection_digest=selection_digest,
            projection_digest=projection_digest,
            projection_version=metadata.get("projectionVersion"),
            scope=metadata.get("scope"),
            freshness=metadata.get("freshness"),
            grounding_digest=metadata.get("groundingDigest"),
            protocol_version="story-context-agent-protocol/v1",
        )

    def _execution_metadata(self, prompt: Prompt) -> PromptExecutionMetadata:
        return PromptExecutionMetadata(
            prompt_version=prompt.prompt_version,
            provider=self._provider.provider_name,
            model_identifier=self._provider.model_identifier,
            prompt_content_digest=prompt.content_digest,
            context_digest=prompt.traceability.context_digest,
            selection_digest=prompt.traceability.selection_digest,
            projection_digest=prompt.traceability.projection_digest,
            projection_version=prompt.traceability.projection_version,
            scope=prompt.traceability.scope,
            freshness=prompt.traceability.freshness,
            grounding_digest=prompt.traceability.grounding_digest,
            protocol_version="story-context-agent-protocol/v1",
        )
