from fastapi.testclient import TestClient

from app.main import create_app


def test_api_key_intake_allows_only_configured_frontend_origin():
    client = TestClient(create_app())

    allowed = client.options(
        "/api/v1/auth/api-key/enroll",
        headers={
            "Origin": "http://127.0.0.1:18083",
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "content-type",
        },
    )
    denied = client.options(
        "/api/v1/auth/api-key/enroll",
        headers={
            "Origin": "https://untrusted.example",
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "content-type",
        },
    )

    assert allowed.status_code == 200
    assert allowed.headers["access-control-allow-origin"] == "http://127.0.0.1:18083"
    assert denied.headers.get("access-control-allow-origin") is None
