from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.schemas.story_context_agent_protocol import (
    StoryContextAgentAccounting,
    StoryContextAgentCallbackIdentity,
    StoryContextAgentSnapshot,
    StoryContextAgentTaskIdentity,
)


def identity(**overrides):
    task_id = uuid4()
    value = {
        "aiTaskId": task_id,
        "snapshotId": task_id,
        "contextDigest": "a" * 64,
        "projectionDigest": "b" * 64,
        "projectionVersion": "sca/v1",
    }
    value.update(overrides)
    return value


def test_snapshot_alias_is_strict_and_extra_fields_are_rejected():
    parsed = StoryContextAgentTaskIdentity.model_validate(identity())
    assert parsed.ai_task_id == parsed.snapshot_id
    with pytest.raises(ValidationError):
        StoryContextAgentTaskIdentity.model_validate({**identity(), "snapshotId": uuid4()})
    with pytest.raises(ValidationError):
        StoryContextAgentTaskIdentity.model_validate({**identity(), "unexpected": True})


def test_accounting_rejects_budget_overrun_and_coercion():
    with pytest.raises(ValidationError):
        StoryContextAgentAccounting.model_validate({
            "candidateCount": 1, "selectedCount": 1, "discardedCount": 0,
            "usedTokens": 2, "budget": 1, "truncated": True,
        })
    with pytest.raises(ValidationError):
        StoryContextAgentAccounting.model_validate({
            "candidateCount": "1", "selectedCount": 1, "discardedCount": 0,
            "usedTokens": 0, "budget": 1, "truncated": False,
        })


def test_snapshot_and_callback_require_protocol_identity():
    base = {
        **identity(),
        "protocolVersion": "story-context-agent-protocol/v1",
        "scope": {"projectSlug": "devlog-ai", "storyId": None, "intent": "sca", "files": []},
    }
    snapshot = StoryContextAgentSnapshot.model_validate({
        **base,
        "accounting": {"candidateCount": 0, "selectedCount": 0, "discardedCount": 0,
                       "usedTokens": 0, "budget": 0, "truncated": False},
        "status": "SUBMITTED",
    })
    assert snapshot.snapshot_id == snapshot.ai_task_id
    callback = StoryContextAgentCallbackIdentity.model_validate({
        **base,
        "freshness": {},
        "groundingDigest": "c" * 64,
    })
    assert callback.protocol_version == "story-context-agent-protocol/v1"


def test_callback_protocol_version_cannot_be_omitted_or_changed():
    base = {
        **identity(),
        "scope": {"projectSlug": "devlog-ai", "storyId": None, "intent": "sca", "files": []},
        "freshness": {},
        "groundingDigest": "c" * 64,
    }
    with pytest.raises(ValidationError):
        StoryContextAgentCallbackIdentity.model_validate(base)
    with pytest.raises(ValidationError):
        StoryContextAgentCallbackIdentity.model_validate({
            **base, "protocolVersion": "story-context-agent-protocol/v2"
        })
