from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest

from app.schemas.binding import BindingRegistrationRequest
from app.services.binding_registry import BindingRegistry


def binding(**overrides):
    values = {
        "bindingId": uuid4(),
        "taskId": uuid4(),
        "provider": "chatgpt",
        "credentialHandle": "vault-ref-123",
        "expiresAt": datetime.now(timezone.utc) + timedelta(minutes=5),
    }
    values.update(overrides)
    return BindingRegistrationRequest.model_validate(values)


def test_registry_persists_and_resolves_binding(tmp_path):
    first = BindingRegistry(str(tmp_path / "bindings.sqlite3"))
    value = binding()
    first.register(value)

    second = BindingRegistry(str(tmp_path / "bindings.sqlite3"))

    assert second.resolve(value.binding_id, value.task_id, value.provider) == "vault-ref-123"


def test_registry_rejects_task_or_provider_mismatch(tmp_path):
    registry = BindingRegistry(str(tmp_path / "bindings.sqlite3"))
    value = binding()
    registry.register(value)

    with pytest.raises(ValueError, match="not valid"):
        registry.resolve(value.binding_id, uuid4(), value.provider)


def test_registry_rejects_expired_binding(tmp_path):
    registry = BindingRegistry(str(tmp_path / "bindings.sqlite3"))
    value = binding(expiresAt=datetime.now(timezone.utc) - timedelta(seconds=1))

    with pytest.raises(ValueError, match="already expired"):
        registry.register(value)


def test_registry_normalizes_product_chatgpt_identity(tmp_path):
    registry = BindingRegistry(str(tmp_path / "bindings.sqlite3"))
    value = binding(provider="openai_chatgpt")
    registry.register(value)

    assert registry.resolve(value.binding_id, value.task_id, "chatgpt") == "vault-ref-123"


def test_registry_rejects_bindings_after_credential_revocation(tmp_path):
    registry = BindingRegistry(str(tmp_path / "bindings.sqlite3"))
    value = binding()
    registry.register(value)
    registry.revoke_credential(value.credential_handle)

    with pytest.raises(ValueError, match="not valid"):
        registry.resolve(value.binding_id, value.task_id, value.provider)
