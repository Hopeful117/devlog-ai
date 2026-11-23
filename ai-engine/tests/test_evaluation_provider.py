import json
import time
from types import SimpleNamespace

import pytest

from app.api.evaluation_provider import complete_chatgpt
from app.security.service_auth import HmacServiceAuthenticator


class FakeRequest:
    method = "POST"
    url = SimpleNamespace(path="/api/v1/evaluations/chatgpt/responses")

    def __init__(self, body: bytes, headers: dict[str, str]) -> None:
        self._body = body
        self.headers = headers

    async def body(self) -> bytes:
        return self._body


@pytest.mark.asyncio
async def test_remote_provider_binding_failure_is_structured(monkeypatch: pytest.MonkeyPatch) -> None:
    body = json.dumps({
        "taskId": "00000000-0000-0000-0000-000000000001",
        "model": "gpt-5.6-luna",
        "instructions": "bounded test",
        "input": "question",
        "maxOutputTokens": 256,
        "text": {},
    }, separators=(",", ":")).encode()
    authenticator = HmacServiceAuthenticator("service-secret")
    headers = authenticator.sign(
        method="POST",
        path="/api/v1/evaluations/chatgpt/responses",
        body=body,
        timestamp=int(time.time()),
        nonce="nonce-1",
        request_id="request-1",
    )
    monkeypatch.setattr(
        "app.api.evaluation_provider._complete",
        lambda _payload: (_ for _ in ()).throw(ValueError("binding unavailable")),
    )

    response = await complete_chatgpt(FakeRequest(body, headers), authenticator)

    assert response.status_code == 409
    assert json.loads(response.body) == {"code": "CREDENTIAL_BINDING_RESOLUTION_FAILED"}
