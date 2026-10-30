from functools import lru_cache
from typing import Annotated

from fastapi import APIRouter, Depends, Request, status
from fastapi.responses import JSONResponse

from app.core.config import get_settings
from app.schemas.binding import (
    BindingRegistrationRequest,
    BindingRegistrationResponse,
    CredentialAssociationVerificationRequest,
    CredentialAssociationVerificationResponse,
)
from app.security.credential_store import CredentialStoreError, EncryptedCredentialStore
from app.security.service_auth import HmacServiceAuthenticator, ServiceAuthenticationError
from app.services.binding_registry import BindingRegistry

router = APIRouter(prefix="/ai/bindings", tags=["ai-bindings"])


def get_authenticator() -> HmacServiceAuthenticator | None:
    settings = get_settings()
    if not settings.service_auth_secret:
        return None
    return HmacServiceAuthenticator(
        settings.service_auth_secret,
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
        credential = EncryptedCredentialStore(
            get_settings().credential_store_path, get_settings().credential_encryption_key
        ).get(payload.credential_handle, payload.provider)
        verified = credential.subject == payload.subject
    except (ServiceAuthenticationError, ValueError, CredentialStoreError):
        return JSONResponse(status_code=status.HTTP_401_UNAUTHORIZED,
                            content={"code": "INVALID_CREDENTIAL_ASSOCIATION"})
    return CredentialAssociationVerificationResponse(verified=verified)
