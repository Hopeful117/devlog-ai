from base64 import urlsafe_b64encode
from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest

from app.schemas.binding import BindingRegistrationRequest
from app.security.credential_resolver import ProviderCredentialResolver
from app.security.credential_store import EncryptedCredentialStore, StoredCredential
from app.services.binding_registry import BindingRegistry


def test_resolver_requires_matching_binding(tmp_path):
    binding_store = BindingRegistry(str(tmp_path / "bindings.sqlite3"))
    credential_store = EncryptedCredentialStore(
        str(tmp_path / "credentials.sqlite3"),
        urlsafe_b64encode(b"0123456789abcdef0123456789abcdef").decode(),
    )
    binding = BindingRegistrationRequest(
        bindingId=uuid4(), taskId=uuid4(), provider="chatgpt",
        credentialHandle="handle-1",
        expiresAt=datetime.now(timezone.utc) + timedelta(minutes=5),
    )
    binding_store.register(binding)
    credential_store.put(StoredCredential(
        handle="handle-1", provider="chatgpt", access_token="token",
        refresh_token=None, access_token_expires_at=None,
    ))
    resolver = ProviderCredentialResolver(binding_store, credential_store)

    resolved = resolver.resolve(binding.binding_id, binding.task_id, "chatgpt")

    assert resolved.access_token == "token"
    with pytest.raises(ValueError, match="not valid"):
        resolver.resolve(binding.binding_id, uuid4(), "chatgpt")
