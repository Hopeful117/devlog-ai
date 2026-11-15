"""Versioned contracts for Core-authorized Story Context investigation proposals."""

from enum import Enum
from typing import Annotated, Any, Literal
from uuid import UUID

from pydantic import Field, RootModel, StrictInt, field_validator, model_validator

from app.schemas.ai_task import ContractModel
from app.schemas.story_context_analysis import ProviderStoryContextAnalysisResult


INVESTIGATION_PROTOCOL_VERSION = "story-context-agent-protocol/v2"
_MAX_INT = 2**31 - 1
_REVISION = r"^\S{1,255}$"
_IDENTIFIER = r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$"


class InvestigationBudget(ContractModel):
    maximum_steps: StrictInt = Field(alias="maximumSteps", ge=1, le=_MAX_INT)
    maximum_retrieval_calls: StrictInt = Field(alias="maximumRetrievalCalls", ge=1, le=_MAX_INT)
    maximum_evidence_items: StrictInt = Field(alias="maximumEvidenceItems", ge=1, le=_MAX_INT)
    maximum_bytes: StrictInt = Field(alias="maximumBytes", ge=1, le=_MAX_INT)
    maximum_tokens: StrictInt = Field(alias="maximumTokens", ge=1, le=_MAX_INT)
    maximum_elapsed_millis: StrictInt = Field(alias="maximumElapsedMillis", ge=1, le=_MAX_INT)
    maximum_provider_output_tokens: StrictInt = Field(
        alias="maximumProviderOutputTokens", ge=1, le=_MAX_INT
    )


class InvestigationScope(ContractModel):
    project_slug: str = Field(alias="projectSlug", min_length=1, max_length=100)
    revision: str = Field(min_length=1, max_length=255, pattern=_REVISION)
    allowed_path_prefixes: list[str] = Field(
        default_factory=list, alias="allowedPathPrefixes", max_length=100
    )

    @field_validator("revision")
    @classmethod
    def revision_must_be_resolvable(cls, value: str) -> str:
        return _resolvable_revision(value)

    @field_validator("allowed_path_prefixes")
    @classmethod
    def validate_prefixes(cls, values: list[str]) -> list[str]:
        return [_canonical_path(value, "allowed path prefix") for value in values]


class InvestigationInitialization(ContractModel):
    protocol_version: Literal["story-context-agent-protocol/v2"] = Field(alias="protocolVersion")
    execution_id: UUID = Field(alias="executionId")
    parent_task_id: UUID = Field(alias="parentTaskId")
    scope: InvestigationScope
    budget: InvestigationBudget
    initialized: Literal[True]


class InvestigationActionProposal(ContractModel):
    action_id: str = Field(alias="actionId", min_length=1, max_length=100, pattern=_IDENTIFIER)
    project_slug: str = Field(alias="projectSlug", min_length=1, max_length=100)
    revision: str = Field(min_length=1, max_length=255, pattern=_REVISION)
    purpose: str = Field(min_length=1, max_length=500)
    result_limit: StrictInt = Field(alias="resultLimit", ge=1, le=_MAX_INT)
    maximum_bytes: StrictInt = Field(alias="maximumBytes", ge=1, le=_MAX_INT)
    maximum_tokens: StrictInt = Field(alias="maximumTokens", ge=1, le=_MAX_INT)

    @field_validator("revision")
    @classmethod
    def revision_must_be_resolvable(cls, value: str) -> str:
        return _resolvable_revision(value)

    @field_validator("purpose")
    @classmethod
    def purpose_must_not_be_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("purpose must not be blank")
        return value


class SearchEvidenceActionProposal(InvestigationActionProposal):
    kind: Literal["SEARCH_EVIDENCE"]
    query: str = Field(min_length=1, max_length=500)
    path_prefix: str | None = Field(default=None, alias="pathPrefix", max_length=500)

    @field_validator("query")
    @classmethod
    def query_must_not_be_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("query must not be blank")
        return value

    @field_validator("path_prefix")
    @classmethod
    def path_prefix_must_be_relative(cls, value: str | None) -> str | None:
        return None if value is None else _canonical_path(value, "pathPrefix")


class ReadEvidenceRangeActionProposal(InvestigationActionProposal):
    kind: Literal["READ_EVIDENCE_RANGE"]
    path: str = Field(min_length=1, max_length=500)
    start_line: StrictInt = Field(alias="startLine", ge=1, le=_MAX_INT)
    end_line: StrictInt = Field(alias="endLine", ge=1, le=_MAX_INT)

    @field_validator("path")
    @classmethod
    def path_must_be_relative(cls, value: str) -> str:
        return _canonical_path(value, "path")

    @model_validator(mode="after")
    def validate_range(self) -> "ReadEvidenceRangeActionProposal":
        if self.end_line < self.start_line:
            raise ValueError("endLine must not be less than startLine")
        return self


InvestigationAction = Annotated[
    SearchEvidenceActionProposal | ReadEvidenceRangeActionProposal,
    Field(discriminator="kind"),
]


class InvestigationPlanProposal(ContractModel):
    protocol_version: Literal["story-context-agent-protocol/v2"] = Field(
        alias="protocolVersion"
    )
    plan_id: str = Field(alias="planId", min_length=1, max_length=100, pattern=_IDENTIFIER)
    execution_id: UUID = Field(alias="executionId")
    parent_task_id: UUID = Field(alias="parentTaskId")
    scope: InvestigationScope
    budget: InvestigationBudget
    actions: list[InvestigationAction] = Field(min_length=1, max_length=100)

    @model_validator(mode="after")
    def validate_actions(self) -> "InvestigationPlanProposal":
        ids = [action.action_id for action in self.actions]
        if len(ids) != len(set(ids)):
            raise ValueError("actionId values must be unique within a plan")
        if len(self.actions) > self.budget.maximum_steps:
            raise ValueError("actions must not exceed maximumSteps")
        for action in self.actions:
            if action.project_slug != self.scope.project_slug:
                raise ValueError("action projectSlug must match scope")
            if action.revision != self.scope.revision:
                raise ValueError("action revision must match scope")
        return self


class InvestigationStopReason(str, Enum):
    COMPLETED = "COMPLETED"
    SUFFICIENT_EVIDENCE = "SUFFICIENT_EVIDENCE"
    BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED"
    NO_RELEVANT_EVIDENCE = "NO_RELEVANT_EVIDENCE"
    REVISION_INVALID = "REVISION_INVALID"
    ACTION_REJECTED = "ACTION_REJECTED"
    PROVIDER_INCOMPLETE = "PROVIDER_INCOMPLETE"
    VALIDATION_FAILED = "VALIDATION_FAILED"


class InvestigationEvidence(ContractModel):
    reference: str = Field(min_length=1, max_length=500)
    kind: str | None = Field(default=None, max_length=100)
    path: str | None = Field(default=None, max_length=500)
    revision: str | None = Field(default=None, max_length=255)
    source_type: str | None = Field(default=None, alias="sourceType", max_length=100)
    identifier: str | None = Field(default=None, max_length=255)
    summary: str | None = Field(default=None, max_length=2000)
    trust_tier: str | None = Field(default=None, alias="trustTier", max_length=100)
    citable: bool | None = None
    content: str | None = Field(default=None, max_length=100000)
    start_line: StrictInt | None = Field(default=None, alias="startLine", ge=1)
    end_line: StrictInt | None = Field(default=None, alias="endLine", ge=1)


class InvestigationCapabilityResult(ContractModel):
    protocol_version: Literal["story-context-agent-protocol/v2"] = Field(
        alias="protocolVersion"
    )
    execution_id: UUID = Field(alias="executionId")
    parent_task_id: UUID = Field(alias="parentTaskId")
    action_id: str = Field(alias="actionId", min_length=1, max_length=100, pattern=_IDENTIFIER)
    kind: Literal["SEARCH_EVIDENCE", "READ_EVIDENCE_RANGE"]
    project_slug: str = Field(alias="projectSlug", min_length=1, max_length=100)
    revision: str = Field(min_length=1, max_length=255, pattern=_REVISION)
    outcome: Literal["RESULTS", "EMPTY", "UNAVAILABLE", "REJECTED", "DUPLICATE"]
    evidence: list[InvestigationEvidence] = Field(default_factory=list, max_length=100)
    stop_reason: InvestigationStopReason | None = Field(default=None, alias="stopReason")
    grounding_contract: dict[str, Any] = Field(default_factory=dict, alias="groundingContract")
    grounding_digest: str | None = Field(default=None, alias="groundingDigest")
    rejection_code: str | None = Field(default=None, alias="rejectionCode", max_length=100)
    rejection_message: str | None = Field(default=None, alias="rejectionMessage", max_length=500)

    @field_validator("revision")
    @classmethod
    def revision_must_be_resolvable(cls, value: str) -> str:
        return _resolvable_revision(value)


class InvestigationActionResponse(ContractModel):
    ai_task_id: UUID = Field(alias="aiTaskId")
    duplicate: bool
    result: InvestigationCapabilityResult


class InvestigationPlanDecision(ContractModel):
    phase: Literal["PLAN"]
    plan: InvestigationPlanProposal


class InvestigationActionDecision(ContractModel):
    phase: Literal["ACTION"]
    action: InvestigationAction


class InvestigationFinalDecision(ContractModel):
    phase: Literal["FINAL"]
    stop_reason: InvestigationStopReason = Field(alias="stopReason")
    result: ProviderStoryContextAnalysisResult


InvestigationDecision = Annotated[
    InvestigationPlanDecision | InvestigationActionDecision | InvestigationFinalDecision,
    Field(discriminator="phase"),
]


class InvestigationDecisionResponse(RootModel[InvestigationDecision]):
    """Provider-facing strict root envelope for one v2 decision."""


def _canonical_path(value: str, field_name: str) -> str:
    value = value.replace("\\", "/")
    if value.startswith("/") or ":/" in value[:4]:
        raise ValueError(f"{field_name} must be relative")
    parts = [part for part in value.split("/") if part not in ("", ".")]
    if not parts or ".." in parts:
        raise ValueError(f"{field_name} contains invalid traversal")
    return "/".join(parts)


def _resolvable_revision(value: str) -> str:
    if value in {"UNKNOWN", "UNSPECIFIED"}:
        raise ValueError("revision must be resolvable")
    return value
