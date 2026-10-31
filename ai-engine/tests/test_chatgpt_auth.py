from types import SimpleNamespace

import pytest
from starlette.responses import Response

from app.api import chatgpt_auth


@pytest.mark.asyncio
async def test_oauth_session_cookie_security_follows_browser_facing_url(monkeypatch):
    class Service:
        def start(self, *args):
            return "https://auth.example/authorize"

    monkeypatch.setattr(chatgpt_auth, "get_oauth_service", lambda: Service())
    monkeypatch.setattr(
        chatgpt_auth,
        "get_settings",
        lambda: SimpleNamespace(public_base_url="http://127.0.0.1:18081"),
    )

    redirect = await chatgpt_auth.start(Response(), devlog_oauth_session=None)

    assert "Secure" not in redirect.headers["set-cookie"]


@pytest.mark.asyncio
async def test_oauth_session_cookie_is_secure_for_https_public_url(monkeypatch):
    class Service:
        def start(self, *args):
            return "https://auth.example/authorize"

    monkeypatch.setattr(chatgpt_auth, "get_oauth_service", lambda: Service())
    monkeypatch.setattr(
        chatgpt_auth,
        "get_settings",
        lambda: SimpleNamespace(public_base_url="https://ai.example"),
    )

    redirect = await chatgpt_auth.start(Response(), devlog_oauth_session=None)

    assert "Secure" in redirect.headers["set-cookie"]
