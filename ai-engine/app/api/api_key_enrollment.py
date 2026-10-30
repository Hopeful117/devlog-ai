import json
from functools import lru_cache
from typing import Annotated

from fastapi import APIRouter, Depends, Request, status
from fastapi.responses import JSONResponse

from app.api.bindings import get_authenticator
from app.core.config import get_settings
from app.security.credential_store import EncryptedCredentialStore
from app.security.service_auth import HmacServiceAuthenticator, ServiceAuthenticationError
from app.services.api_key_enrollment import ApiKeyEnrollmentError, ApiKeyEnrollmentService

router = APIRouter(prefix="/auth/api-key", tags=["api-key-auth"])


@lru_cache
def get_enrollment_service() -> ApiKeyEnrollmentService:
    settings = get_settings()
    return ApiKeyEnrollmentService(
        settings,
        EncryptedCredentialStore(settings.credential_store_path, settings.credential_encryption_key),
    )


@router.post("/enroll")
async def enroll(request: Request):
    raw_body = await request.body()
    try:
        payload = json.loads(raw_body)
        return await get_enrollment_service().enroll(
            intent_id=str(payload["intentId"]), provider=str(payload["provider"]),
            authentication_method=str(payload["authenticationMethod"]),
            issued_at=int(payload["issuedAt"]),
            expires_at=int(payload["expiresAt"]), principal_binding=str(payload["principalBinding"]),
            signature=str(payload["signature"]), api_key=str(payload["apiKey"]),
        )
    except (ApiKeyEnrollmentError, KeyError, TypeError, ValueError):
        return JSONResponse(status_code=status.HTTP_400_BAD_REQUEST,
                            content={"code": "API_KEY_ENROLLMENT_FAILED"})


@router.post("/compensate", status_code=status.HTTP_204_NO_CONTENT, response_model=None)
async def compensate(request: Request,
                     authenticator: Annotated[HmacServiceAuthenticator | None, Depends(get_authenticator)]):
    if authenticator is None:
        return JSONResponse(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            content={"code": "SERVICE_AUTHENTICATION_UNAVAILABLE"})
    raw_body = await request.body()
    try:
        authenticator.verify(method=request.method, path=request.url.path, body=raw_body,
                             headers=request.headers)
        get_enrollment_service().compensate(str(json.loads(raw_body)["credentialHandle"]))
    except (ServiceAuthenticationError, KeyError, TypeError, ValueError):
        return JSONResponse(status_code=status.HTTP_401_UNAUTHORIZED,
                            content={"code": "INVALID_SERVICE_AUTHENTICATION"})
