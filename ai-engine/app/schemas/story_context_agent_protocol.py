"""Strict Story Context Agent Protocol v1 task and callback contracts."""

from typing import Any, Literal
from uuid import UUID

from pydantic import Field, StrictBool, StrictInt, model_validator

from app.schemas.ai_task import ContractModel


PROTOCOL_VERSION = "story-context-agent-protocol/v1"
PROJECTION_VERSION = "sca/v1"
SHA256 = r"^[0-9a-f]{64}$"


class StoryContextAgentScope(ContractModel):
    project_slug: str = Field(alias="projectSlug", min_length=1)
    story_id: UUID | None = Field(alias="storyId")
    intent: str = Field(min_length=1)
    files: list[str] = Field(default_factory=list)


class StoryContextAgentAccounting(ContractModel):
    candidate_count: StrictInt = Field(alias="candidateCount", ge=0)
    selected_count: StrictInt = Field(alias="selectedCount", ge=0)
    discarded_count: StrictInt = Field(alias="discardedCount", ge=0)
    used_tokens: StrictInt = Field(alias="usedTokens", ge=0)
    budget: StrictInt = Field(ge=0)
    truncated: StrictBool
    warnings: list[dict[str, Any]] = Field(default_factory=list)

    @model_validator(mode="after")
    def enforce_budget(self):
        if self.used_tokens > self.budget:
            raise ValueError("usedTokens must not exceed budget")
        return self


class StoryContextAgentTaskIdentity(ContractModel):
    ai_task_id: UUID = Field(alias="aiTaskId")
    snapshot_id: UUID = Field(alias="snapshotId")
    context_digest: str = Field(alias="contextDigest", pattern=SHA256)
    projection_digest: str = Field(alias="projectionDigest", pattern=SHA256)
    projection_version: Literal["sca/v1"] = Field(alias="projectionVersion")

    @model_validator(mode="after")
    def enforce_snapshot_alias(self):
        if self.ai_task_id != self.snapshot_id:
            raise ValueError("snapshotId must equal aiTaskId")
        return self


class StoryContextAgentCallbackIdentity(StoryContextAgentTaskIdentity):
    protocol_version: Literal["story-context-agent-protocol/v1"] = Field(alias="protocolVersion")
    scope: StoryContextAgentScope
    freshness: dict[str, Any]
    grounding_digest: str = Field(alias="groundingDigest", pattern=SHA256)


class StoryContextAgentSnapshot(StoryContextAgentTaskIdentity):
    protocol_version: Literal["story-context-agent-protocol/v1"] = Field(alias="protocolVersion")
    scope: StoryContextAgentScope
    accounting: StoryContextAgentAccounting
    status: str = Field(min_length=1)
