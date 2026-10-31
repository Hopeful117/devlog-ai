import secrets
import json
from functools import lru_cache

from fastapi import APIRouter, Cookie, Depends, Query, Request, Response, status
from fastapi.responses import JSONResponse, RedirectResponse

from app.core.config import get_settings
from app.security.credential_store import CredentialStoreError, EncryptedCredentialStore
from app.services.chatgpt_oauth import ChatGptOAuthService, OAuthError, OAuthSessionStore
from app.api.bindings import get_authenticator
from app.security.service_auth import HmacServiceAuthenticator, ServiceAuthenticationError

router = APIRouter(prefix="/auth/chatgpt", tags=["chatgpt-auth"])
SESSION_COOKIE = "devlog_oauth_session"


@lru_cache
def get_oauth_service() -> ChatGptOAuthService:
    settings = get_settings()
    return ChatGptOAuthService(
        settings,
        OAuthSessionStore(settings.oauth_store_path),
        EncryptedCredentialStore(settings.credential_store_path,
                                 settings.credential_encryption_key),
    )


def session_id(cookie: str | None) -> str:
    return cookie or secrets.token_urlsafe(32)


@router.get("/start")
async def start(response: Response, intentId: str | None = Query(default=None),
                intentProvider: str | None = Query(default=None),
                intentExpiresAt: int | None = Query(default=None),
                intentPrincipalId: str | None = Query(default=None),
                intentSignature: str | None = Query(default=None),
                devlog_oauth_session: str | None = Cookie(default=None)):
    current = session_id(devlog_oauth_session)
    try:
        location = get_oauth_service().start(current, intentId, intentProvider,
                                             intentExpiresAt, intentSignature, intentPrincipalId)
    except (OAuthError, CredentialStoreError, OSError):
        return JSONResponse(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            content={"code": "CHATGPT_AUTHORIZATION_UNAVAILABLE"})
    redirect = RedirectResponse(url=location, status_code=status.HTTP_307_TEMPORARY_REDIRECT)
    if devlog_oauth_session is None:
        redirect.set_cookie(SESSION_COOKIE, current, httponly=True,
                            secure=get_settings().public_base_url.lower().startswith("https://"),
                            samesite="lax", max_age=3600)
    return redirect


@router.get("/callback")
async def callback(
        code: str | None = Query(default=None),
    state: str | None = Query(default=None),
    error: str | None = Query(default=None),
    client_id: str | None = Query(default=None),
    scope: str | None = Query(default=None),
        devlog_oauth_session: str | None = Cookie(default=None),
):
    if not devlog_oauth_session or not state:
        return JSONResponse(status_code=status.HTTP_400_BAD_REQUEST,
                            content={"code": "INVALID_CHATGPT_CALLBACK"})
    try:
        result = await get_oauth_service().callback(
            devlog_oauth_session, state, code or "", error, client_id, scope
        )
    except OAuthError:
        return RedirectResponse(
            url=f"{get_settings().chatgpt_frontend_redirect_uri}?oauth=failed",
            status_code=status.HTTP_303_SEE_OTHER,
        )
    if isinstance(result, dict) and result.get("status") == "PENDING_CORE_CONFIRMATION":
        try:
            await get_oauth_service().complete_core_configuration(
                str(result["intentId"]), str(result["completionCode"])
            )
        except (OAuthError, KeyError):
            return RedirectResponse(
                url=f"{get_settings().chatgpt_frontend_redirect_uri}?oauth=failed",
                status_code=status.HTTP_303_SEE_OTHER,
            )
        return RedirectResponse(
            url=f"{get_settings().chatgpt_frontend_redirect_uri}?oauth=connected",
            status_code=status.HTTP_303_SEE_OTHER,
        )
    return {"status": result}


@router.post("/completion/redeem")
async def redeem_completion(
        request: Request,
        authenticator: HmacServiceAuthenticator | None = Depends(get_authenticator),
):
    if authenticator is None:
        return JSONResponse(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            content={"code": "SERVICE_AUTHENTICATION_UNAVAILABLE"})
    raw_body = await request.body()
    try:
        authenticator.verify(method=request.method, path=request.url.path, body=raw_body,
                             headers=request.headers)
        payload = json.loads(raw_body)
        result = get_oauth_service().redeem_completion(
            str(payload["intentId"]), str(payload["completionCode"])
        )
    except (ServiceAuthenticationError, OAuthError, KeyError, ValueError):
        return JSONResponse(status_code=status.HTTP_401_UNAUTHORIZED,
                            content={"code": "INVALID_OAUTH_COMPLETION"})
    return result


@router.post("/completion/ack", status_code=status.HTTP_204_NO_CONTENT, response_model=None)
async def acknowledge_completion(
        request: Request,
        authenticator: HmacServiceAuthenticator | None = Depends(get_authenticator),
) -> Response | JSONResponse:
    if authenticator is None:
        return JSONResponse(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            content={"code": "SERVICE_AUTHENTICATION_UNAVAILABLE"})
    raw_body = await request.body()
    try:
        authenticator.verify(method=request.method, path=request.url.path, body=raw_body,
                             headers=request.headers)
        payload = json.loads(raw_body)
        get_oauth_service().acknowledge_completion(
            str(payload["intentId"]), str(payload["completionCode"])
        )
    except (ServiceAuthenticationError, OAuthError, KeyError, ValueError):
        return JSONResponse(status_code=status.HTTP_401_UNAUTHORIZED,
                            content={"code": "INVALID_OAUTH_COMPLETION"})
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/completion/compensate", status_code=status.HTTP_204_NO_CONTENT, response_model=None)
async def compensate_completion(
        request: Request,
        authenticator: HmacServiceAuthenticator | None = Depends(get_authenticator),
) -> Response | JSONResponse:
    if authenticator is None:
        return JSONResponse(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            content={"code": "SERVICE_AUTHENTICATION_UNAVAILABLE"})
    raw_body = await request.body()
    try:
        authenticator.verify(method=request.method, path=request.url.path, body=raw_body,
                             headers=request.headers)
        payload = json.loads(raw_body)
        get_oauth_service().compensate_completion(
            str(payload["intentId"]), str(payload["completionCode"])
        )
    except (ServiceAuthenticationError, OAuthError, KeyError, ValueError):
        return JSONResponse(status_code=status.HTTP_401_UNAUTHORIZED,
                            content={"code": "INVALID_OAUTH_COMPLETION"})
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/status")
async def auth_status(devlog_oauth_session: str | None = Cookie(default=None)):
    if not devlog_oauth_session:
        return {"status": "DISCONNECTED", "scopes": []}
    status_value, scopes = get_oauth_service().status(devlog_oauth_session)
    return {"status": status_value, "scopes": scopes.split() if scopes else []}


@router.post("/disconnect", status_code=status.HTTP_204_NO_CONTENT)
async def disconnect(devlog_oauth_session: str | None = Cookie(default=None)) -> Response:
    if devlog_oauth_session:
        get_oauth_service().disconnect(devlog_oauth_session)
    response = Response(status_code=status.HTTP_204_NO_CONTENT)
    response.delete_cookie(SESSION_COOKIE)
    return response
