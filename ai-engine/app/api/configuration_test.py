import json
from functools import lru_cache
from typing import Annotated

from fastapi import APIRouter, Depends, Request, status
from fastapi.responses import JSONResponse

from app.api.bindings import get_authenticator
from app.core.config import get_settings
from app.schemas.binding import ConfigurationTestRequest
from app.security.credential_store import EncryptedCredentialStore
from app.security.service_auth import HmacServiceAuthenticator, ServiceAuthenticationError
from app.services.configuration_test import ConfigurationTestService

router = APIRouter(prefix="/ai/configuration", tags=["configuration-test"])


@lru_cache
def get_configuration_test_service() -> ConfigurationTestService:
    settings = get_settings()
    return ConfigurationTestService(
        settings,
        EncryptedCredentialStore(settings.credential_store_path, settings.credential_encryption_key),
    )


@router.post("/test")
async def test_configuration(
        request: Request,
        authenticator: Annotated[HmacServiceAuthenticator | None, Depends(get_authenticator)],
):
    if authenticator is None:
        return JSONResponse(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            content={"code": "SERVICE_AUTHENTICATION_UNAVAILABLE"})
    raw_body = await request.body()
    try:
        authenticator.verify(method=request.method, path=request.url.path, body=raw_body,
                             headers=request.headers)
        payload = ConfigurationTestRequest.model_validate_json(raw_body)
        return await get_configuration_test_service().test(
            provider=payload.provider,
            authentication_method=payload.authentication_method,
            model=payload.model,
            credential_handle=payload.credential_handle,
        )
    except (ServiceAuthenticationError, ValueError, KeyError, TypeError, json.JSONDecodeError):
        return JSONResponse(status_code=status.HTTP_401_UNAUTHORIZED,
                            content={"code": "INVALID_SERVICE_AUTHENTICATION"})
