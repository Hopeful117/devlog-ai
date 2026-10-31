from functools import lru_cache
from datetime import datetime, timezone
import sqlite3
from typing import Annotated

from fastapi import APIRouter, Depends, Request, Response, status
from fastapi.responses import JSONResponse

from app.core.config import get_settings
from app.schemas.binding import (
    BindingRegistrationRequest,
    BindingRegistrationResponse,
    CredentialAssociationVerificationRequest,
    CredentialAssociationVerificationResponse,
    CredentialRevocationRequest,
)
from app.security.credential_store import CredentialStoreError, EncryptedCredentialStore
from app.security.service_auth import HmacServiceAuthenticator, ServiceAuthenticationError
from app.services.chatgpt_oauth import OAuthError, OAuthSessionStore, ChatGptOAuthService
from app.services.binding_registry import BindingRegistry
from app.services.provider_identity import engine_provider

router = APIRouter(prefix="/ai/bindings", tags=["ai-bindings"])


@lru_cache
def get_authenticator() -> HmacServiceAuthenticator | None:
    settings = get_settings()
    secret = settings.service_auth_secret or settings.sca_callback_secret
    if not secret:
        return None
    return HmacServiceAuthenticator(
        secret,
        max_clock_skew_seconds=settings.service_auth_max_clock_skew_seconds,
    )


@lru_cache
def get_binding_registry() -> BindingRegistry:
    return BindingRegistry(get_settings().binding_store_path)


@router.post("/register", response_model=BindingRegistrationResponse,
             status_code=status.HTTP_201_CREATED)
async def register_binding(
        request: Request,
        authenticator: Annotated[HmacServiceAuthenticator | None, Depends(get_authenticator)],
) -> BindingRegistrationResponse | JSONResponse:
    if authenticator is None:
        return JSONResponse(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            content={"code": "SERVICE_AUTHENTICATION_UNAVAILABLE"})
    raw_body = await request.body()
    credential = None
    try:
        authenticator.verify(
            method=request.method,
            path=request.url.path,
            body=raw_body,
            headers=request.headers,
        )
        binding = BindingRegistrationRequest.model_validate_json(raw_body)
        get_binding_registry().register(binding)
    except (ServiceAuthenticationError, ValueError):
        return JSONResponse(status_code=status.HTTP_401_UNAUTHORIZED,
                            content={"code": "INVALID_SERVICE_AUTHENTICATION"})
    return BindingRegistrationResponse(
        bindingId=binding.binding_id,
        taskId=binding.task_id,
        provider=binding.provider,
        expiresAt=binding.expires_at,
    )


@router.post("/credentials/verify", response_model=CredentialAssociationVerificationResponse)
async def verify_credential_association(
        request: Request,
        authenticator: Annotated[HmacServiceAuthenticator | None, Depends(get_authenticator)],
) -> CredentialAssociationVerificationResponse | JSONResponse:
    if authenticator is None:
        return JSONResponse(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            content={"code": "SERVICE_AUTHENTICATION_UNAVAILABLE"})
    raw_body = await request.body()
    try:
        authenticator.verify(method=request.method, path=request.url.path, body=raw_body,
                             headers=request.headers)
        payload = CredentialAssociationVerificationRequest.model_validate_json(raw_body)
        credential_store = EncryptedCredentialStore(
            get_settings().credential_store_path, get_settings().credential_encryption_key
        )
        provider = engine_provider(payload.provider)
        credential = credential_store.get(payload.credential_handle, provider)
        if provider == "chatgpt":
            settings = get_settings()
            credential = await ChatGptOAuthService(
                settings,
                OAuthSessionStore(settings.oauth_store_path),
                credential_store,
            ).refresh_if_expired(payload.credential_handle)
        expected_kind = {
            "API_KEY": "API_KEY",
            "OAUTH": "OAUTH",
        }.get(payload.authentication_method)
        if expected_kind is None:
            raise ValueError("unsupported authentication method")
        if expected_kind == "API_KEY":
            if payload.intent_id is not None or payload.principal_binding:
                if payload.intent_id is None or not payload.principal_binding:
                    raise ValueError("API-key enrollment context is incomplete")
                with sqlite3.connect(get_settings().binding_store_path) as connection:
                    bound = connection.execute(
                        "SELECT 1 FROM api_key_credential_intents "
                        "WHERE handle = ? AND intent_id = ? AND principal_binding = ?",
                        (payload.credential_handle, str(payload.intent_id), payload.principal_binding),
                    ).fetchone()
                if bound is None:
                    raise ValueError("credential is not bound to enrollment intent")
        verified = credential.kind == expected_kind and (
            credential.subject is None
            if expected_kind == "API_KEY"
            else credential.subject == payload.subject
        )
    except (ServiceAuthenticationError, ValueError, CredentialStoreError, OAuthError):
        return JSONResponse(status_code=status.HTTP_401_UNAUTHORIZED,
                            content={"code": "INVALID_CREDENTIAL_ASSOCIATION"})
    if credential is None:
        return JSONResponse(status_code=status.HTTP_401_UNAUTHORIZED,
                            content={"code": "INVALID_CREDENTIAL_ASSOCIATION"})
    expired = credential.access_token_expires_at is not None \
        and credential.access_token_expires_at <= datetime.now(timezone.utc)
    usable = expected_kind == "API_KEY" or required_scopes(credential.scopes)
    state = "PLAN_USAGE_AUTHORIZED" if verified and not expired and usable \
        else "REAUTH_REQUIRED" if verified else "REVOKED"
    return CredentialAssociationVerificationResponse(
        verified=verified, state=state, scopes=credential.scopes if verified else ""
    )


@router.post("/credentials/revoke", status_code=status.HTTP_204_NO_CONTENT, response_model=None)
async def revoke_credential(
        request: Request,
        authenticator: Annotated[HmacServiceAuthenticator | None, Depends(get_authenticator)],
) -> Response | JSONResponse:
    if authenticator is None:
        return JSONResponse(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            content={"code": "SERVICE_AUTHENTICATION_UNAVAILABLE"})
    raw_body = await request.body()
    try:
        authenticator.verify(method=request.method, path=request.url.path, body=raw_body,
                             headers=request.headers)
        payload = CredentialRevocationRequest.model_validate_json(raw_body)
        credential_store = EncryptedCredentialStore(
            get_settings().credential_store_path, get_settings().credential_encryption_key
        )
        credential_store.delete(payload.credential_handle)
        get_binding_registry().revoke_credential(payload.credential_handle)
    except (ServiceAuthenticationError, ValueError, CredentialStoreError):
        return JSONResponse(status_code=status.HTTP_401_UNAUTHORIZED,
                            content={"code": "INVALID_SERVICE_AUTHENTICATION"})
    return Response(status_code=status.HTTP_204_NO_CONTENT)


def required_scopes(scopes: str) -> bool:
    return {"resource.invoke", "chatgpt.tokens.use.direct"}.issubset(set(scopes.split()))
