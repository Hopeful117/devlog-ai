import secrets
from functools import lru_cache

from fastapi import APIRouter, Cookie, Depends, Query, Response, status
from fastapi.responses import JSONResponse, RedirectResponse

from app.core.config import get_settings
from app.security.credential_store import CredentialStoreError, EncryptedCredentialStore
from app.services.chatgpt_oauth import ChatGptOAuthService, OAuthError, OAuthSessionStore

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
async def start(response: Response, devlog_oauth_session: str | None = Cookie(default=None)):
    current = session_id(devlog_oauth_session)
    try:
        location = get_oauth_service().start(current)
    except (OAuthError, CredentialStoreError, OSError):
        return JSONResponse(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            content={"code": "CHATGPT_AUTHORIZATION_UNAVAILABLE"})
    redirect = RedirectResponse(url=location, status_code=status.HTTP_307_TEMPORARY_REDIRECT)
    if devlog_oauth_session is None:
        redirect.set_cookie(SESSION_COOKIE, current, httponly=True,
                            secure=location.startswith("https://"),
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
        return JSONResponse(status_code=status.HTTP_400_BAD_REQUEST,
                            content={"code": "CHATGPT_AUTHORIZATION_FAILED"})
    return {"status": result}


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
