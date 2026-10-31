from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
from uuid import uuid4

import pytest

from app.clients.core_callback_client import CoreCallbackClient
from app.models.ai_task import AiTaskType
from app.schemas.ai_task import PromptRequest
from app.services.binding_registry import BindingRegistry
from app.services.task_processing_service import AiTaskProcessingService
from tests.intent_fixtures import prompt_request
from app.schemas.binding import BindingRegistrationRequest


def authorized_submission(provider: str, model: str, binding_id, task_id) -> PromptRequest:
    value = prompt_request().model_copy(update={
        "task_type": AiTaskType.STORY_CONTEXT_ANALYSIS,
        "ai_task_id": task_id,
        "credential_binding_id": binding_id,
        "provider": provider,
        "model_identifier": model,
        "projection_digest": "a" * 64,
    })
    return value


def service(registry, provider_name="chatgpt", model="gpt-5.6-luna"):
    return AiTaskProcessingService(
        insight_generation_service=None,
        callback_client=CoreCallbackClient,
        story_context_analysis_service=SimpleNamespace(
            provider=SimpleNamespace(provider_name=provider_name, model_identifier=model)
        ),
        binding_registry=registry,
    )


@pytest.mark.parametrize(
    ("provider", "model", "engine_provider"),
    [("chatgpt", "gpt-5.6-luna", "chatgpt"), ("anthropic", "claude-opus-5-5", "anthropic")],
)
def test_story_agent_requires_exact_core_binding(tmp_path, provider, model, engine_provider):
    registry = BindingRegistry(str(tmp_path / f"{provider}.sqlite3"))
    binding_id, task_id = uuid4(), uuid4()
    registry.register(BindingRegistrationRequest.model_validate({
        "bindingId": binding_id,
        "taskId": task_id,
        "provider": engine_provider,
        "credentialHandle": f"handle-{provider}",
        "expiresAt": datetime.now(timezone.utc) + timedelta(minutes=5),
    }))

    submission = authorized_submission(provider, model, binding_id, task_id)
    service(registry, provider, model).authorize_submission(submission)


def test_story_agent_rejects_missing_or_mismatched_binding(tmp_path):
    registry = BindingRegistry(str(tmp_path / "bindings.sqlite3"))
    task_id = uuid4()
    missing = prompt_request().model_copy(update={
        "task_type": AiTaskType.STORY_CONTEXT_ANALYSIS,
        "ai_task_id": task_id,
        "projection_digest": "a" * 64,
    })
    with pytest.raises(ValueError, match="binding"):
        service(registry).authorize_submission(missing)

    binding_id = uuid4()
    registry.register(BindingRegistrationRequest.model_validate({
        "bindingId": binding_id,
        "taskId": task_id,
        "provider": "chatgpt",
        "credentialHandle": "handle",
        "expiresAt": datetime.now(timezone.utc) + timedelta(minutes=5),
    }))
    mismatch = authorized_submission("anthropic", "claude-opus-5-5", binding_id, task_id)
    with pytest.raises(ValueError, match="binding"):
        service(registry).authorize_submission(mismatch)
