import pytest

from app.security.service_auth import (
    HmacServiceAuthenticator,
    ReplayProtector,
    ServiceAuthenticationError,
    canonical_json,
)


def test_canonical_json_is_stable() -> None:
    assert canonical_json({"b": 2, "a": "é"}) == '{"a":"é","b":2}'.encode()


def test_hmac_authenticator_accepts_signed_request_once() -> None:
    authenticator = HmacServiceAuthenticator(
        "binding-secret", replay_protector=ReplayProtector(retention_seconds=120)
    )
    body = canonical_json({"bindingId": "binding-1", "taskId": "task-1"})
    headers = authenticator.sign(
        method="POST", path="/internal/ai/bindings", body=body,
        timestamp=1_000, nonce="nonce-1", request_id="request-1",
    )

    authenticator.verify(
        method="POST", path="/internal/ai/bindings", body=body,
        headers=headers, now=1_010,
    )

    with pytest.raises(ServiceAuthenticationError, match="replayed"):
        authenticator.verify(
            method="POST", path="/internal/ai/bindings", body=body,
            headers=headers, now=1_010,
        )


@pytest.mark.parametrize("tamper", ["method", "path", "body", "request_id"])
def test_hmac_authenticator_rejects_tampering(tamper: str) -> None:
    authenticator = HmacServiceAuthenticator("binding-secret")
    body = b'{"bindingId":"binding-1"}'
    headers = authenticator.sign(
        method="POST", path="/internal/ai/bindings", body=body,
        timestamp=1_000, nonce="nonce-1", request_id="request-1",
    )
    method, path, request_body = "POST", "/internal/ai/bindings", body
    if tamper == "method":
        method = "PUT"
    elif tamper == "path":
        path = "/internal/ai/other"
    elif tamper == "body":
        request_body = b'{"bindingId":"binding-2"}'
    else:
        headers["X-DevLog-Service-Request-Id"] = "request-2"

    with pytest.raises(ServiceAuthenticationError):
        authenticator.verify(
            method=method, path=path, body=request_body,
            headers=headers, now=1_000,
        )


def test_hmac_authenticator_rejects_stale_request_without_claiming_nonce() -> None:
    authenticator = HmacServiceAuthenticator("binding-secret")
    body = b"{}"
    headers = authenticator.sign(
        method="POST", path="/internal/ai/bindings", body=body,
        timestamp=1_000, nonce="nonce-1", request_id="request-1",
    )

    with pytest.raises(ServiceAuthenticationError, match="expired"):
        authenticator.verify(
            method="POST", path="/internal/ai/bindings", body=body,
            headers=headers, now=1_061,
        )
