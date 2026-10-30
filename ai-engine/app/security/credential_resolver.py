from uuid import UUID

from app.security.credential_store import EncryptedCredentialStore, StoredCredential
from app.services.binding_registry import BindingRegistry
from app.services.provider_identity import engine_provider


class ProviderCredentialResolver:
    """Resolves a provider credential only after binding scope validation."""

    def __init__(self, bindings: BindingRegistry, credentials: EncryptedCredentialStore) -> None:
        self._bindings = bindings
        self._credentials = credentials

    def resolve(self, binding_id: UUID, task_id: UUID, provider: str) -> StoredCredential:
        provider = engine_provider(provider)
        handle = self._bindings.resolve(binding_id, task_id, provider)
        return self._credentials.get(handle, provider)

    def resolve_for_task(self, task_id: UUID, provider: str) -> StoredCredential:
        provider = engine_provider(provider)
        handle = self._bindings.resolve_for_task(task_id, provider)
        return self._credentials.get(handle, provider)

    def replace_tokens(self, handle: str, access_token: str,
                       refresh_token: str | None, expires_at) -> None:
        self._credentials.replace_tokens(handle, access_token, refresh_token, expires_at)

    def invalidate(self, handle: str) -> None:
        self._credentials.delete(handle)
