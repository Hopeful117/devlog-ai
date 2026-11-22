"""Provider-free tests for Q1 live readiness and authorization boundaries."""

from __future__ import annotations

import json
import math
import socket
from types import SimpleNamespace
from pathlib import Path

import pytest

from evaluations.devlog_ai_abc_runner import LIVE, LIVE_PREFLIGHT, run
from evaluations.devlog_ai_abc_runner.live import (
    LiveAuthorizationError,
    canonical_manifest_digest,
    live_preflight,
    load_q1_oracle,
    validate_live_authorization,
)
from evaluations.devlog_ai_abc_runner.live_execution import (
    ChatGptOAuthTransport,
    CoreLiveTreatmentAdapter,
    LiveConfiguration,
    LiveConfigurationError,
    _bounded_mcp_error,
    load_environment_file,
    live_adapters,
    missing_live_provider_environment,
    require_live_provider_environment,
    resolve_live_story,
)
from evaluations.comparative_baseline.collection_runtime import ProviderRequest, TransportFailure
from evaluations.devlog_ai_abc_runner.pricing import canonical_pricing_digest


ROOT = Path(__file__).parents[1].parent
MANIFEST_PATH = ROOT / "docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json"
ORACLE_PATH = ROOT / "docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json"
REVISION = "60fae3e0e1104d0ffbe3832c1f578ed532b1268a"


def manifest() -> dict:
    return json.loads(MANIFEST_PATH.read_text())


def accepted_oracle() -> dict:
    oracle = load_q1_oracle(ORACLE_PATH, expected_revision=REVISION)
    return {**oracle, "status": "ACCEPTED"}


def test_env_example_quotes_shell_value_with_spaces() -> None:
    env_example = (ROOT / ".env.example").read_text()

    assert 'CHATGPT_AGENT_NAME="DevLog AI"' in env_example


def test_load_environment_file_preserves_explicit_values_and_parses_quotes(tmp_path: Path) -> None:
    env_file = tmp_path / ".env"
    env_file.write_text(
        'CHATGPT_AGENT_NAME="DevLog AI"\n'
        "EXPLICIT=value-from-file\n"
        "export EMPTY=\n",
        encoding="utf-8",
    )
    environment = {"EXPLICIT": "from-process"}

    loaded = load_environment_file(env_file, environ=environment)

    assert loaded == ("CHATGPT_AGENT_NAME", "EMPTY")
    assert environment == {
        "EXPLICIT": "from-process",
        "CHATGPT_AGENT_NAME": "DevLog AI",
        "EMPTY": "",
    }


def test_live_provider_environment_reports_names_only() -> None:
    environment = {
        "LLM_PROVIDER": "openai_chatgpt",
        "CHATGPT_OAUTH_ENABLED": "true",
        "CHATGPT_REDIRECT_URI": "http://127.0.0.1:18081/api/v1/auth/chatgpt/callback",
    }

    assert missing_live_provider_environment(environment) == (
        "CHATGPT_EXT_AGENT_HOST_ID",
        "DEVLOG_CREDENTIAL_ENCRYPTION_KEY",
        "DEVLOG_SERVICE_AUTH_SECRET",
    )
    with pytest.raises(LiveConfigurationError, match="CHATGPT_EXT_AGENT_HOST_ID"):
        require_live_provider_environment(environment)


def test_chatgpt_oauth_transport_exposes_bounded_http_diagnostic(monkeypatch: pytest.MonkeyPatch) -> None:
    class Credential:
        access_token = "access-token"
        access_token_expires_at = None
        scopes = "resource.invoke chatgpt.tokens.use.direct"

    class Response:
        status_code = 401

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

        def read(self):
            return b""

    class Client:
        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return None

        def stream(self, *_args, **_kwargs):
            return Response()

    transport = object.__new__(ChatGptOAuthTransport)
    transport._task_id = "task-id"
    transport._model = "gpt-5.6-luna"
    transport._settings = SimpleNamespace(llm_timeout_seconds=1, chatgpt_responses_endpoint="https://provider.example/responses")
    transport._resolver = SimpleNamespace(resolve_for_task=lambda *_args: Credential())
    transport._refresh_service = None
    transport._required_scopes_granted = lambda scopes: True
    monkeypatch.setattr("evaluations.devlog_ai_abc_runner.live_execution.httpx.Client", lambda **_kwargs: Client())

    request = ProviderRequest(
        assignment=SimpleNamespace(),
        mode="AGENT_DIRECT_OPEN",
        turn_index=1,
        input_payload={"conversation": [{"role": "user", "content": "question"}]},
        tool_schema=None,
        max_output_tokens=10,
    )

    with pytest.raises(TransportFailure) as captured:
        transport.complete(request)

    diagnostic = captured.value.attribution
    assert diagnostic["category"] == "AUTHENTICATION_ERROR"
    assert diagnostic["httpStatus"] == 401
    assert diagnostic["requestPhase"] == "PROVIDER_RESPONSE"
    assert diagnostic["transportAttempted"] is True
    assert diagnostic["providerResponseReceived"] is True
    assert "access-token" not in json.dumps(diagnostic)
    assert diagnostic["bindingTaskId"] == "task-id"
    assert diagnostic["bindingProvider"] == "chatgpt"
    assert len(diagnostic["bindingStorePathDigest"]) == 64


def test_core_live_adapter_preserves_mcp_shape_when_task_identity_is_missing() -> None:
    adapter = CoreLiveTreatmentAdapter(
        "A_DEVLOG_CURRENT",
        SimpleNamespace(project_slug="devlog-ai", story_id=None, story_number=223, story_path=None, files=("docs/story.md",)),
        manifest(),
        SimpleNamespace(tool=lambda *_args, **_kwargs: {"status": "COMPLETED", "diagnostics": {"code": "TASK_ID_MISSING"}}),
    )

    result = adapter.execute({"observationId": "Q1-A-DEVLOG-CURRENT-r1"}, manifest())

    assert result["executionStatus"] == "RUNTIME_FAILURE"
    assert result["errorType"] == "MCP_TASK_ID_MISSING"
    assert result["mcpResponse"]["status"] == "COMPLETED"
    assert result["mcpResponse"]["diagnosticKeys"] == ["code"]
    assert len(result["mcpResponse"]["responseDigest"]) == 64


def test_core_live_adapter_rejects_completed_response_without_result() -> None:
    adapter = CoreLiveTreatmentAdapter(
        "C_DEVLOG_ENGINEERING_ANALYST",
        SimpleNamespace(project_slug="devlog-ai", story_id=None, story_number=223, story_path=None, files=("docs/story.md",)),
        manifest(),
        SimpleNamespace(tool=lambda *_args, **_kwargs: {"status": "COMPLETED", "aiTaskId": "task-id"}),
    )

    result = adapter.execute({"observationId": "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1"}, manifest())

    assert result["executionStatus"] == "RUNTIME_FAILURE"
    assert result["errorType"] == "MCP_COMPLETED_RESULT_UNAVAILABLE"
    assert result["taskId"] == "task-id"


def approved_pricing() -> dict:
    value = {
        "status": "APPROVED",
        "version": "q1-test-pricing-v1",
        "currency": "USD",
        "inputPerMillion": "2",
        "outputPerMillion": "4",
    }
    value["digest"] = canonical_pricing_digest(value)
    return value


def valid_authorization() -> dict:
    value = manifest()
    oracle = accepted_oracle()
    return {
        "authorizationStatus": "AUTHORIZED",
        "approverId": "q1-test-approver",
        "approvedAt": "2026-01-01T00:00:00Z",
        "campaignId": value["manifestId"],
        "manifestId": value["manifestId"],
        "manifestVersion": value["version"],
        "manifestDigest": canonical_manifest_digest(value),
        "oracleStatus": "Q1_ORACLE_ACCEPTED",
        "oracleDigest": oracle["canonicalDigest"],
        "authorizedObservationIds": [item["observationId"] for item in value["observations"]],
        "authorizedConditions": value["conditions"],
        "repositoryRevision": value["repositoryRevision"],
        "runtimeRevision": "a" * 64,
        "provider": value["provider"],
        "model": value["model"],
        "budgets": value["technicalBudgets"],
        "limits": value["technicalBudgets"],
        "pricingStatus": "APPROVED",
        "pricingVersion": approved_pricing()["version"],
        "pricingDigest": approved_pricing()["digest"],
        "costCeiling": {"currency": "USD", "maxAmount": 1.0},
        "expiresAtEpochSeconds": 4_000_000_000,
    }


def test_live_preflight_is_static_and_fails_closed(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LLM_API_KEY", "must-not-be-read")

    def fail_network(*_args: object, **_kwargs: object) -> None:
        raise AssertionError("network access is forbidden in LIVE_PREFLIGHT")

    monkeypatch.setattr(socket.socket, "connect", fail_network)
    report = run(
        mode=LIVE_PREFLIGHT,
        manifest_path=MANIFEST_PATH,
        oracle_path=ORACLE_PATH,
        output_root=tmp_path / "q1-live-run",
        repository_root=ROOT,
    )

    assert report["status"] == "Q1_LIVE_PREFLIGHT_BLOCKED"
    assert report["runnerStatus"] == "Q1_LIVE_ADAPTERS_AVAILABLE"
    assert report["oracleStatus"] == "Q1_ORACLE_ACCEPTED"
    assert report["authorizationStatus"] == "Q1_LIVE_NOT_AUTHORIZED"
    assert report["providerCalls"] == 0
    assert report["networkCalls"] == 0
    assert len(report["entrypoints"]) == 3
    assert all(item["status"] == "LIVE_ENTRYPOINT_IDENTIFIED" for item in report["entrypoints"])
    assert all(item["adapterStatus"] == "CONTROLLED_ADAPTER_AVAILABLE" for item in report["entrypoints"])
    assert report["provenance"]["sourceSnapshot"]["revision"] == REVISION
    assert report["provenance"]["runtimeRevision"] != REVISION
    assert "executed runtime revision does not match the frozen manifest" in report["criticalBlockers"]
    assert all(item["status"] == "LIVE_ENTRYPOINT_IDENTIFIED" for item in report["entrypoints"])
    assert not (tmp_path / "q1-live-run").exists()


def test_valid_future_authorization_contract_is_accepted_by_validator_only() -> None:
    validate_live_authorization(
        valid_authorization(),
        manifest=manifest(),
        oracle=accepted_oracle(),
        pricing_contract=approved_pricing(),
        now_epoch_seconds=3_000_000_000,
    )


def test_non_metered_oauth_authorization_uses_operational_ceiling_only() -> None:
    authorization = valid_authorization()
    pricing = {
        "status": "APPROVED",
        "version": "q1-chatgpt-oauth-pricing-v1",
        "currency": "NOT_APPLICABLE",
        "billingModel": "CHATGPT_OAUTH_SUBSCRIPTION_NOT_METERED",
        "inputPerMillion": None,
        "outputPerMillion": None,
        "costCeilingPolicy": "OPERATIONAL_LIMITS_ONLY",
    }
    pricing["digest"] = canonical_pricing_digest(pricing)
    authorization.update({
        "pricingVersion": pricing["version"],
        "pricingDigest": pricing["digest"],
        "costCeiling": {"currency": "NOT_APPLICABLE", "maxAmount": "NOT_MEASURED"},
    })

    validate_live_authorization(
        authorization,
        manifest=manifest(),
        oracle=accepted_oracle(),
        pricing_contract=pricing,
        now_epoch_seconds=3_000_000_000,
    )


@pytest.mark.parametrize(
    ("field", "value", "message"),
    [
        ("manifestDigest", "0" * 64, "manifest digest"),
        ("repositoryRevision", "other-revision", "repository revision"),
        ("authorizedObservationIds", ["Q1-A-DEVLOG-CURRENT-r1"], "observation set"),
        ("expiresAtEpochSeconds", 2_000_000_000, "expired"),
        ("budgets", {"maxModelTurns": 9}, "limits"),
        ("pricingStatus", "PENDING", "pricing"),
        ("apiKey", "secret", "credentials"),
    ],
)
def test_invalid_authorization_is_rejected_closed(field: str, value: object, message: str) -> None:
    authorization = valid_authorization()
    authorization[field] = value
    with pytest.raises(LiveAuthorizationError, match=message):
        validate_live_authorization(
            authorization,
            manifest=manifest(),
            oracle=accepted_oracle(),
            pricing_contract=approved_pricing(),
            now_epoch_seconds=3_000_000_000,
        )


def test_pending_oracle_cannot_authorize_live_execution() -> None:
    pending_oracle = {**load_q1_oracle(ORACLE_PATH, expected_revision=REVISION), "status": "CORRECTION_PROPOSED"}
    with pytest.raises(LiveAuthorizationError, match="not explicitly accepted"):
        validate_live_authorization(
            valid_authorization(),
            manifest=manifest(),
            oracle=pending_oracle,
            pricing_contract=approved_pricing(),
            now_epoch_seconds=3_000_000_000,
        )


def test_authorization_requires_the_exact_approved_pricing_artifact() -> None:
    authorization = valid_authorization()
    authorization["pricingDigest"] = "0" * 64
    with pytest.raises(LiveAuthorizationError, match="pricing digest"):
        validate_live_authorization(
            authorization,
            manifest=manifest(),
            oracle=accepted_oracle(),
            pricing_contract=approved_pricing(),
            now_epoch_seconds=3_000_000_000,
        )


def test_non_finite_cost_ceiling_is_rejected() -> None:
    authorization = valid_authorization()
    authorization["costCeiling"]["maxAmount"] = math.inf
    with pytest.raises(LiveAuthorizationError, match="maxAmount"):
        validate_live_authorization(
            authorization,
            manifest=manifest(),
            oracle=accepted_oracle(),
            pricing_contract=approved_pricing(),
            now_epoch_seconds=3_000_000_000,
        )


def test_live_configuration_requires_core_boundary_and_bounded_files() -> None:
    with pytest.raises(LiveConfigurationError, match="DEVLOG_LIVE_PROJECT_SLUG"):
        LiveConfiguration.from_environment({})

    configuration = LiveConfiguration.from_environment({
        "DEVLOG_LIVE_PROJECT_SLUG": "devlog-ai",
        "DEVLOG_LIVE_STORY_NUMBER": "223",
        "DEVLOG_LIVE_FILES": "docs/a.md, docs/b.md",
        "DEVLOG_LIVE_B_TASK_ID": "00000000-0000-0000-0000-000000000001",
        "DEVLOG_MCP_LAUNCHER": "/tmp/devlog-mcp-start.sh",
    })
    assert configuration.mcp_launcher == Path("/tmp/devlog-mcp-start.sh")
    assert configuration.story_number == 223
    assert configuration.story_id is None
    assert configuration.files == ("docs/a.md", "docs/b.md")
    assert str(configuration.b_task_id) == "00000000-0000-0000-0000-000000000001"
    assert not hasattr(configuration, "core_bearer_token")


def test_core_live_adapter_uses_authenticated_mcp_without_core_bearer() -> None:
    class FakeMcp:
        def __init__(self) -> None:
            self.calls = []

        def tool(self, name: str, arguments: dict) -> dict:
            self.calls.append((name, arguments))
            return {
                "status": "COMPLETED",
                "aiTaskId": "task-1",
                "result": {"analysis": "bounded"},
            }

    mcp = FakeMcp()
    config = LiveConfiguration.from_environment({
        "DEVLOG_LIVE_PROJECT_SLUG": "devlog-ai",
        "DEVLOG_LIVE_STORY_NUMBER": "223",
        "DEVLOG_LIVE_FILES": "docs/a.md",
        "DEVLOG_MCP_LAUNCHER": "/tmp/devlog-mcp-start.sh",
    }, require_task_id=False)
    adapter = CoreLiveTreatmentAdapter("A_DEVLOG_CURRENT", config, {}, mcp)

    result = adapter.execute({"observationId": "Q1-A-DEVLOG-CURRENT-r1"}, {})

    assert result["executionStatus"] == "COMPLETED"
    assert result["submission"]["status"] == "MCP_ACCEPTED"
    assert mcp.calls[0][0] == "devlog_story_agent"
    assert "storyId" not in mcp.calls[0][1]
    assert "Authorization" not in mcp.calls[0][1]
    assert "DEVLOG_CORE_BEARER_TOKEN" not in repr(mcp.calls[0][1])


def test_live_adapters_require_chatgpt_oauth_before_provider_execution() -> None:
    configuration = LiveConfiguration.from_environment({
        "DEVLOG_LIVE_PROJECT_SLUG": "devlog-ai",
        "DEVLOG_LIVE_STORY_NUMBER": "223",
        "DEVLOG_LIVE_FILES": "docs/a.md",
        "DEVLOG_LIVE_B_TASK_ID": "00000000-0000-0000-0000-000000000001",
        "DEVLOG_MCP_LAUNCHER": "/tmp/devlog-mcp-start.sh",
    })

    with pytest.raises(LiveConfigurationError, match="CHATGPT_OAUTH_ENABLED=true"):
        live_adapters(
            repository_root=ROOT,
            manifest={"model": "gpt-5.6-luna"},
            config=configuration,
            mcp=object(),
            environment={"LLM_PROVIDER": "openai_chatgpt"},
        )

    adapters = live_adapters(
        repository_root=ROOT,
        manifest={"model": "gpt-5.6-luna"},
        config=configuration,
        mcp=object(),
        environment={
            "LLM_PROVIDER": "openai_chatgpt",
            "CHATGPT_OAUTH_ENABLED": "true",
            "LLM_MODEL": "gpt-5.6-luna",
        },
    )
    assert set(adapters) == {"A_DEVLOG_CURRENT", "B_DIRECT_INVESTIGATOR", "C_DEVLOG_ENGINEERING_ANALYST"}


def test_mcp_error_diagnostic_is_bounded_and_redacts_credentials() -> None:
    error = RuntimeError(json.dumps({
        "code": -32001,
        "message": "Bearer abc123 token=secret-value request failed",
    }))

    diagnostic = _bounded_mcp_error(error)

    assert diagnostic.startswith("-32001: ")
    assert "abc123" not in diagnostic
    assert "secret-value" not in diagnostic
    assert "[REDACTED]" in diagnostic


def test_story_number_is_resolved_before_uuid_scoped_live_calls() -> None:
    class FakeMcp:
        def tool(self, name: str, arguments: dict) -> dict:
            assert name == "resolve_engineering_story"
            assert arguments == {"projectSlug": "devlog-ai", "storyNumber": 223}
            return {
                "id": "00000000-0000-0000-0000-000000000223",
                "storyNumber": 223,
                "storyPath": "docs/a.md",
            }

    config = LiveConfiguration.from_environment({
        "DEVLOG_LIVE_PROJECT_SLUG": "devlog-ai",
        "DEVLOG_LIVE_STORY_NUMBER": "223",
        "DEVLOG_LIVE_FILES": "docs/a.md",
        "DEVLOG_MCP_LAUNCHER": "/tmp/devlog-mcp-start.sh",
    }, require_task_id=False)

    resolved = resolve_live_story(config, FakeMcp())

    assert resolved.story_id == "00000000-0000-0000-0000-000000000223"
    assert resolved.story_path == "docs/a.md"


def test_story_resolution_rejects_path_outside_configured_files() -> None:
    class FakeMcp:
        def tool(self, name: str, arguments: dict) -> dict:
            return {
                "id": "00000000-0000-0000-0000-000000000223",
                "storyNumber": 223,
                "storyPath": "docs/other.md",
            }

    config = LiveConfiguration.from_environment({
        "DEVLOG_LIVE_PROJECT_SLUG": "devlog-ai",
        "DEVLOG_LIVE_STORY_NUMBER": "223",
        "DEVLOG_LIVE_FILES": "docs/a.md",
        "DEVLOG_MCP_LAUNCHER": "/tmp/devlog-mcp-start.sh",
    }, require_task_id=False)

    with pytest.raises(LiveConfigurationError, match="outside the configured files"):
        resolve_live_story(config, FakeMcp())


def test_live_mode_remains_disabled_even_when_a_future_contract_is_valid(tmp_path: Path) -> None:
    with pytest.raises(LiveAuthorizationError, match="approved pricing contract is missing"):
        run(
            mode=LIVE,
            manifest_path=MANIFEST_PATH,
            oracle_path=ORACLE_PATH,
            output_root=tmp_path / "q1-live-run",
            repository_root=ROOT,
            authorization=valid_authorization(),
        )
