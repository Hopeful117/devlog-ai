from __future__ import annotations

from collections.abc import Mapping
import hashlib
import hmac
import json
import threading
import time
from typing import Any


SERVICE_SIGNATURE_HEADER = "X-DevLog-Service-Signature"
SERVICE_TIMESTAMP_HEADER = "X-DevLog-Service-Timestamp"
SERVICE_NONCE_HEADER = "X-DevLog-Service-Nonce"
SERVICE_REQUEST_ID_HEADER = "X-DevLog-Service-Request-Id"


class ServiceAuthenticationError(ValueError):
    """Raised without including secret-bearing request details."""


def canonical_json(value: Any) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def canonical_request(method: str, path: str, timestamp: str, nonce: str,
                      request_id: str, body: bytes) -> bytes:
    if not all((method, path, timestamp, nonce, request_id)):
        raise ServiceAuthenticationError("invalid service authentication request")
    prefix = "\n".join((method.upper(), path, timestamp, nonce, request_id)).encode("utf-8")
    return prefix + b"\n" + body


class ReplayProtector:
    def __init__(self, *, retention_seconds: int = 300) -> None:
        if retention_seconds <= 0:
            raise ValueError("retention_seconds must be positive")
        self._retention_seconds = retention_seconds
        self._seen: dict[str, float] = {}
        self._lock = threading.Lock()

    def claim(self, nonce: str, *, now: float | None = None) -> bool:
        current = time.time() if now is None else now
        with self._lock:
            cutoff = current - self._retention_seconds
            self._seen = {
                value: seen_at
                for value, seen_at in self._seen.items()
                if seen_at >= cutoff
            }
            if nonce in self._seen:
                return False
            self._seen[nonce] = current
            return True


class HmacServiceAuthenticator:
    def __init__(self, secret: str, *, max_clock_skew_seconds: int = 60,
                 replay_protector: ReplayProtector | None = None) -> None:
        if not secret:
            raise ValueError("service authentication secret must not be blank")
        if max_clock_skew_seconds <= 0:
            raise ValueError("max_clock_skew_seconds must be positive")
        self._secret = secret.encode("utf-8")
        self._max_clock_skew_seconds = max_clock_skew_seconds
        self._replay_protector = replay_protector or ReplayProtector(
            retention_seconds=max_clock_skew_seconds * 2
        )

    def sign(self, *, method: str, path: str, body: bytes, timestamp: int,
             nonce: str, request_id: str) -> dict[str, str]:
        timestamp_value = str(timestamp)
        payload = canonical_request(
            method, path, timestamp_value, nonce, request_id, body
        )
        signature = hmac.new(self._secret, payload, hashlib.sha256).hexdigest()
        return {
            SERVICE_SIGNATURE_HEADER: f"sha256={signature}",
            SERVICE_TIMESTAMP_HEADER: timestamp_value,
            SERVICE_NONCE_HEADER: nonce,
            SERVICE_REQUEST_ID_HEADER: request_id,
        }

    def verify(self, *, method: str, path: str, body: bytes,
               headers: Mapping[str, str], now: int | None = None) -> None:
        timestamp = headers.get(SERVICE_TIMESTAMP_HEADER)
        nonce = headers.get(SERVICE_NONCE_HEADER)
        request_id = headers.get(SERVICE_REQUEST_ID_HEADER)
        supplied = headers.get(SERVICE_SIGNATURE_HEADER)
        if not all((timestamp, nonce, request_id, supplied)):
            raise ServiceAuthenticationError("invalid service authentication request")
        try:
            timestamp_number = int(timestamp)
        except (TypeError, ValueError) as error:
            raise ServiceAuthenticationError("invalid service authentication request") from error
        current = int(time.time() if now is None else now)
        if abs(current - timestamp_number) > self._max_clock_skew_seconds:
            raise ServiceAuthenticationError("expired service authentication request")
        if not supplied.startswith("sha256="):
            raise ServiceAuthenticationError("invalid service authentication request")
        encoded = supplied.removeprefix("sha256=")
        if len(encoded) != 64:
            raise ServiceAuthenticationError("invalid service authentication request")
        try:
            expected = bytes.fromhex(encoded)
        except ValueError as error:
            raise ServiceAuthenticationError("invalid service authentication request") from error
        payload = canonical_request(method, path, timestamp, nonce, request_id, body)
        actual = hmac.new(self._secret, payload, hashlib.sha256).digest()
        if not hmac.compare_digest(actual, expected):
            raise ServiceAuthenticationError("invalid service authentication request")
        if not self._replay_protector.claim(f"{request_id}:{nonce}", now=current):
            raise ServiceAuthenticationError("replayed service authentication request")
