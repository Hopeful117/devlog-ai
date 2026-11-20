from __future__ import annotations

import base64
import importlib.util
import io
import json
import os
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "runtime.py"
SPEC = importlib.util.spec_from_file_location("devlog_runtime", MODULE_PATH)
assert SPEC and SPEC.loader
runtime = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runtime)


class RuntimeTests(unittest.TestCase):
    def test_required_configuration_uses_local_keycloak_issuer_defaults(self) -> None:
        with patch.dict(
            os.environ,
            {},
            clear=True,
        ):
            configuration = runtime.required_configuration()

        self.assertEqual(configuration["issuer_uri"], "http://127.0.0.1:18084/realms/devlog")
        self.assertEqual(
            configuration["jwks_uri"],
            "http://127.0.0.1:18084/realms/devlog/protocol/openid-connect/certs",
        )

    def test_acquire_token_returns_bounded_lease(self) -> None:
        class Response:
            def __enter__(self):
                return self

            def __exit__(self, *args):
                return False

            def read(self):
                return b'{"access_token":"secret-token","expires_in":60}'

        with tempfile.TemporaryDirectory() as directory:
            secret_file = Path(directory) / "secret"
            secret_file.write_text("client-secret")
            configuration = {
                "secret_file": str(secret_file),
                "token_endpoint": "https://keycloak.invalid/token",
                "actor_client_id": "devlog-mcp",
            }
            with patch.object(runtime.urllib.request, "urlopen", return_value=Response()):
                lease = runtime.acquire_token(configuration)

        self.assertEqual(lease.token, "secret-token")
        self.assertGreater(lease.expires_at, time.time())

    def test_acquire_token_rejects_response_without_expiry(self) -> None:
        class Response:
            def __enter__(self):
                return self

            def __exit__(self, *args):
                return False

            def read(self):
                return b'{"access_token":"not-a-jwt"}'

        with tempfile.TemporaryDirectory() as directory:
            secret_file = Path(directory) / "secret"
            secret_file.write_text("client-secret")
            configuration = {
                "secret_file": str(secret_file),
                "token_endpoint": "https://keycloak.invalid/token",
                "actor_client_id": "devlog-mcp",
            }
            with patch.object(runtime.urllib.request, "urlopen", return_value=Response()):
                with self.assertRaisesRegex(RuntimeError, "TOKEN_RESPONSE_INVALID"):
                    runtime.acquire_token(configuration)

    def test_token_endpoint_timeout_is_retried_once(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"token_retry_backoff_seconds": "0"},
                Path(directory),
                writer,
                runtime.TokenLease("old-token", time.time() + 3600),
            )
            renewed = runtime.TokenLease("new-token", time.time() + 3600)
            with patch.object(
                runtime,
                "acquire_token",
                side_effect=[TimeoutError("token endpoint timed out"), renewed],
            ) as acquire, patch.object(
                session.stop_requested, "wait", return_value=False
            ) as wait:
                result = session.acquire_token_with_retry()

        self.assertIs(result, renewed)
        self.assertEqual(acquire.call_count, 2)
        wait.assert_called_once_with(timeout=0.0)
        self.assertEqual(writer.events[0][0], "TOKEN_RENEWAL_RETRY")
        self.assertEqual(writer.events[0][1]["code"], "TOKEN_ENDPOINT_TIMEOUT")
        self.assertEqual(writer.events[0][1]["attempt"], 1)
        self.assertEqual(writer.events[0][1]["maxAttempts"], 1)

    def test_token_endpoint_500_is_terminal_without_retry(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        error = runtime.urllib.error.HTTPError(
            "https://keycloak.invalid/token", 500, "server error", {}, None
        )
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"token_retry_backoff_seconds": "0"},
                Path(directory),
                Writer(),
                runtime.TokenLease("old-token", time.time() + 3600),
            )
            with patch.object(runtime, "acquire_token", side_effect=error) as acquire:
                with self.assertRaises(runtime.urllib.error.HTTPError) as raised:
                    session.acquire_token_with_retry()

        acquire.assert_called_once()
        self.assertEqual(raised.exception.code, 500)

    def test_token_endpoint_502_is_retried_once(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        error = runtime.urllib.error.HTTPError(
            "https://keycloak.invalid/token", 502, "bad gateway", {}, None
        )
        renewed = runtime.TokenLease("new-token", time.time() + 3600)
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"token_retry_backoff_seconds": "0"},
                Path(directory),
                Writer(),
                runtime.TokenLease("old-token", time.time() + 3600),
            )
            with patch.object(
                runtime, "acquire_token", side_effect=[error, renewed]
            ) as acquire, patch.object(runtime.time, "sleep"):
                result = session.acquire_token_with_retry()

        self.assertIs(result, renewed)
        self.assertEqual(acquire.call_count, 2)

    def test_token_endpoint_transient_classification_is_bounded(self) -> None:
        for status in (502, 503, 504):
            error = runtime.urllib.error.HTTPError(
                "https://keycloak.invalid/token", status, "gateway error", {}, None
            )
            self.assertTrue(runtime.RuntimeSession.is_transient_token_error(error))

        self.assertTrue(
            runtime.RuntimeSession.is_transient_token_error(
                runtime.urllib.error.URLError(TimeoutError("timed out"))
            )
        )
        self.assertFalse(
            runtime.RuntimeSession.is_transient_token_error(
                runtime.urllib.error.URLError(ConnectionRefusedError())
            )
        )
        self.assertFalse(
            runtime.RuntimeSession.is_transient_token_error(
                runtime.urllib.error.HTTPError(
                    "https://keycloak.invalid/token", 429, "rate limited", {}, None
                )
            )
        )

    def test_token_retry_backoff_rejects_excessive_value(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"token_retry_backoff_seconds": str(runtime.MAX_TOKEN_RETRY_BACKOFF_SECONDS + 1)},
                Path(directory),
                Writer(),
                runtime.TokenLease("token", time.time() + 3600),
            )
            with self.assertRaisesRegex(RuntimeError, "CONFIGURATION_INVALID"):
                _ = session.token_retry_backoff

    def test_shutdown_during_renewal_does_not_start_replacement_child(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"token_retry_backoff_seconds": "0"},
                Path(directory),
                writer,
                runtime.TokenLease("old-token", time.time() + 3600),
            )
            renewed = runtime.TokenLease("new-token", time.time() + 3600)
            session.stop_requested.set()
            with patch.object(session, "acquire_token_with_retry", return_value=renewed), patch.object(
                session, "start_child"
            ) as start_child, patch.object(session, "stop_child") as stop_child:
                with self.assertRaisesRegex(RuntimeError, "MCP_REAUTH_REQUIRED"):
                    session._renew(force=True)

        start_child.assert_not_called()
        stop_child.assert_not_called()
        failed = [event for event in writer.events if event[0] == "TOKEN_RENEWAL_FAILED"]
        self.assertEqual(failed[-1][1]["code"], "MCP_INSTANCE_STOPPING")

    def test_acquire_token_rejects_non_object_and_non_finite_expiry(self) -> None:
        class Response:
            def __init__(self, body):
                self.body = body

            def __enter__(self):
                return self

            def __exit__(self, *args):
                return False

            def read(self):
                return self.body

        with tempfile.TemporaryDirectory() as directory:
            secret_file = Path(directory) / "secret"
            secret_file.write_text("client-secret")
            configuration = {
                "secret_file": str(secret_file),
                "token_endpoint": "https://keycloak.invalid/token",
                "actor_client_id": "devlog-mcp",
            }
            for body in (
                b"[]",
                b'{"access_token":"token","expires_in":"NaN"}',
                b'{"access_token":"token","expires_in":"1e10000"}',
                b'{"access_token":"not.%%%%.jwt"}',
                b'{"access_token":"header.W10.sig"}',
                b'\xff',
            ):
                with patch.object(runtime.urllib.request, "urlopen", return_value=Response(body)):
                    with self.assertRaisesRegex(RuntimeError, "TOKEN_RESPONSE_INVALID"):
                        runtime.acquire_token(configuration)
            payload = base64.urlsafe_b64encode(b'{"exp":4102444800}').decode().rstrip("=")
            body = json.dumps({"access_token": f"header.{payload}.sig", "expires_in": "invalid"}).encode()
            with patch.object(runtime.urllib.request, "urlopen", return_value=Response(body)):
                with self.assertRaisesRegex(RuntimeError, "TOKEN_RESPONSE_INVALID"):
                    runtime.acquire_token(configuration)

    def test_replacement_initialize_response_is_consumed(self) -> None:
        class Writer:
            def write(self, *args, **kwargs) -> None:
                pass

        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                Writer(),
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.initialize_id = "1"
            session.suppress_initialize_response = True

            consumed = session.consume_initialize_response(
                {"jsonrpc": "2.0", "id": 1, "result": {"protocolVersion": "2025-06-18"}}
            )

        self.assertTrue(consumed)
        self.assertTrue(session.ready.is_set())

    def test_initial_initialize_response_is_forwarded_and_marks_ready(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append(event)

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.initialize_id = "1"

            forwarded = session.consume_initialize_response(
                {"jsonrpc": "2.0", "id": 1, "result": {"protocolVersion": "2025-06-18"}}
            )

        self.assertFalse(forwarded)
        self.assertTrue(session.ready.is_set())
        self.assertIn("MCP_INSTANCE_READY", writer.events)

    def test_untrackable_request_id_is_rejected(self) -> None:
        class Writer:
            def write(self, *args, **kwargs):
                pass

        class Child:
            stdin = io.BytesIO()

            def poll(self):
                return None

            def terminate(self):
                pass

            def wait(self, timeout=None):
                return 0

        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                Writer(),
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            with self.assertRaisesRegex(RuntimeError, "MCP_PROTOCOL_INVALID"):
                session.forward(b'{"jsonrpc":"2.0","id":null}\n', {"id": None})

    def test_duplicate_request_ids_are_rejected_while_in_flight(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            stdin = io.BytesIO()

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n'
            session.forward(raw, json.loads(raw))

            # Capture the in-flight operation before the collision attempt
            operation = next(iter(session.operations.values()))
            operation_id = operation.operation_id

            # Collision: second forward with the same in-flight ID must be rejected
            with self.assertRaisesRegex(RuntimeError, "MCP_PROTOCOL_INVALID"):
                session.forward(raw, json.loads(raw))

            # Initial operation must still be alive and unchanged
            self.assertIn(operation_id, session.operations)
            self.assertIs(session.operations[operation_id], operation)
            self.assertEqual(operation.state, runtime.TOOL_ACTIVE)

            # JSON-RPC reservation must remain intact
            jsonrpc_key = json.dumps(1, sort_keys=True)
            self.assertIn(jsonrpc_key, session.outstanding)
            self.assertIs(session.outstanding[jsonrpc_key], operation)

            # No second MCP_TOOL_STARTED must have been emitted
            started = [e for e in writer.events if e[0] == "MCP_TOOL_STARTED"]
            self.assertEqual(len(started), 1)
            self.assertEqual(started[0][1]["operationId"], operation_id)

            # Subsequent response must be processed normally
            session.observe_response({"jsonrpc": "2.0", "id": 1, "result": {}})

            # Exactly one terminal event and it carries the original operationId
            terminal = [
                e for e in writer.events
                if e[0] in {"MCP_TOOL_RESPONDED", "MCP_TOOL_FAILED", "MCP_TOOL_INTERRUPTED"}
            ]
            self.assertEqual(len(terminal), 1)
            self.assertEqual(terminal[0][0], "MCP_TOOL_RESPONDED")
            self.assertEqual(terminal[0][1]["operationId"], operation_id)

        # Final cleanup: operation and reservation are both released
        self.assertEqual(session.operations, {})

    def test_tool_operation_emits_started_and_responded_with_stable_identity(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            stdin = io.BytesIO()

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n'
            session.forward(raw, json.loads(raw))
            operation = next(iter(session.operations.values()))
            operation_id = operation.operation_id
            session.observe_response({"jsonrpc": "2.0", "id": 1, "result": {}})

        self.assertEqual([event[0] for event in writer.events], ["MCP_TOOL_STARTED", "MCP_TOOL_RESPONDED"])
        self.assertEqual(writer.events[0][1]["operationId"], operation_id)
        self.assertEqual(writer.events[1][1]["operationId"], operation_id)
        self.assertEqual(writer.events[0][1]["schemaVersion"], runtime.TOOL_SCHEMA_VERSION)
        self.assertEqual(writer.events[1][1]["status"], runtime.TOOL_RESPONDED)

    def test_tool_event_write_does_not_hold_runtime_lock(self) -> None:
        class Writer:
            def __init__(self):
                self.lock_owned_during_write = None

            def write(self, event, **fields):
                self.lock_owned_during_write = session.lock._is_owned()

        class Child:
            stdin = io.BytesIO()

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n'
            session.forward(raw, json.loads(raw))

        self.assertFalse(writer.lock_owned_during_write)

    def test_tool_operation_marks_jsonrpc_error_failed(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            stdin = io.BytesIO()

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":"req-1","method":"tools/call"}\n'
            session.forward(raw, json.loads(raw))
            session.observe_response(
                {"jsonrpc": "2.0", "id": "req-1", "error": {"code": -32601}},
                generation=session.generation,
            )

        self.assertEqual(writer.events[-1][0], "MCP_TOOL_FAILED")
        self.assertEqual(writer.events[-1][1]["reasonCode"], "MCP_JSONRPC_ERROR")
        self.assertEqual(session.operations, {})

    def test_request_id_reuse_after_terminal_state_gets_new_operation_id(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            stdin = io.BytesIO()

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call"}\n'
            session.forward(raw, json.loads(raw))
            first_id = next(iter(session.operations.values())).operation_id
            session.observe_response({"jsonrpc": "2.0", "id": 1, "result": {}})
            session.forward(raw, json.loads(raw))
            second_id = next(iter(session.operations.values())).operation_id

        self.assertNotEqual(first_id, second_id)
        self.assertEqual(len([event for event in writer.events if event[0] == "MCP_TOOL_STARTED"]), 2)

    def test_exhausted_auth_retry_emits_one_terminal_failure(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            stdin = io.BytesIO()

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call"}\n'
            failure = {"jsonrpc": "2.0", "id": 1, "error": {"code": -32600, "message": "Authentication is required"}}
            session.forward(raw, json.loads(raw))
            with patch.object(session, "renew_if_needed"):
                self.assertTrue(session.retry_authentication(failure))
            self.assertFalse(session.retry_authentication(failure))

        failed = [event for event in writer.events if event[0] == "MCP_TOOL_FAILED"]
        self.assertEqual(len(failed), 1)
        self.assertEqual(failed[0][1]["reasonCode"], "MCP_REAUTH_REQUIRED")

    def test_core_authentication_error_does_not_trigger_transport_retry(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        class Child:
            stdin = io.BytesIO()

        message = {
            "jsonrpc": "2.0",
            "id": 1,
            "error": {
                "code": -32600,
                "message": "Authentication is required",
                "data": {"source": "CORE", "category": "CORE_AUTHENTICATION"},
            },
        }

        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                Writer(),
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_engineering_context"}}\n'
            session.forward(raw, json.loads(raw))
            with patch.object(session, "renew_if_needed") as renew:
                self.assertFalse(session.retry_authentication(message))
                renew.assert_not_called()

        self.assertFalse(runtime.RuntimeSession.is_transport_auth_failure(message))

    def test_structured_jsonrpc_errors_keep_layer_specific_diagnostics(self) -> None:
        for category, expected in (
            ("CORE_AUTHENTICATION", "CORE_AUTHENTICATION_FAILED"),
            ("CORE_AUTHORIZATION", "CORE_AUTHORIZATION_FAILED"),
            ("DELEGATION_AUTHENTICATION", "DELEGATION_FAILED"),
            ("PROVIDER_AUTHENTICATION", "PROVIDER_FAILED"),
        ):
            message = {
                "jsonrpc": "2.0",
                "id": 1,
                "error": {"data": {"source": "STRUCTURED", "category": category}},
            }
            self.assertEqual(runtime.RuntimeSession.jsonrpc_error_reason(message), expected)

        self.assertEqual(
            runtime.RuntimeSession.jsonrpc_error_reason(
                {"jsonrpc": "2.0", "id": 1, "error": {"message": "failed"}}
            ),
            "MCP_JSONRPC_ERROR",
        )

    def test_non_idempotent_tool_is_not_replayed_after_transport_auth_failure(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        class Child:
            stdin = io.BytesIO()

        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                Writer(),
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"sync_project_history"}}\n'
            session.forward(raw, json.loads(raw))
            failure = {"jsonrpc": "2.0", "id": 1, "error": {"code": -32600, "message": "Authentication is required"}}
            with patch.object(session, "renew_if_needed") as renew:
                self.assertFalse(session.retry_authentication(failure))
                renew.assert_not_called()

        self.assertEqual(session.operations, {})

    def test_renewal_failure_emits_reauth_failure_and_releases_id(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            stdin = io.BytesIO()

            def poll(self):
                return None

            def terminate(self):
                pass

            def wait(self, timeout=None):
                return 0

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call"}\n'
            failure = {"jsonrpc": "2.0", "id": 1, "error": {"code": -32600, "message": "Authentication is required"}}
            session.forward(raw, json.loads(raw))
            with patch.object(session, "renew_if_needed", side_effect=RuntimeError("MCP_REAUTH_REQUIRED")):
                self.assertFalse(session.retry_authentication(failure))

        failed = [event for event in writer.events if event[0] == "MCP_TOOL_FAILED"]
        self.assertEqual(len(failed), 1)
        self.assertEqual(failed[0][1]["reasonCode"], "MCP_REAUTH_REQUIRED")
        self.assertEqual(session.operations, {})

    def test_retry_updates_current_generation_and_keeps_initial_generation(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            stdin = io.BytesIO()

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.generation = 4
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call"}\n'
            failure = {"jsonrpc": "2.0", "id": 1, "error": {"code": -32600, "message": "Authentication is required"}}
            session.forward(raw, json.loads(raw))
            operation = next(iter(session.operations.values()))
            with patch.object(session, "renew_if_needed", side_effect=lambda **kwargs: setattr(session, "generation", 5)):
                self.assertTrue(session.retry_authentication(failure))
            session.observe_response({"jsonrpc": "2.0", "id": 1, "result": {}}, generation=5)

        responded = [event for event in writer.events if event[0] == "MCP_TOOL_RESPONDED"]
        self.assertEqual(operation.initial_generation, 4)
        self.assertEqual(operation.current_generation, 5)
        self.assertEqual(responded[0][1]["generation"], 5)

    def test_event_contract_does_not_log_jsonrpc_data(self) -> None:
        class Child:
            stdin = io.BytesIO()

        with tempfile.TemporaryDirectory() as directory:
            event_writer = runtime.EventWriter(Path(directory), "instance-1")
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                event_writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":"raw-id","method":"tools/call","params":{"secret":"payload-secret"}}\n'
            session.forward(raw, json.loads(raw))
            session.observe_response(
                {"jsonrpc": "2.0", "id": "raw-id", "result": {"secret": "result-secret"}},
                generation=session.generation,
            )
            content = (Path(directory) / "events.jsonl").read_text(encoding="utf-8")

        self.assertNotIn("raw-id", content)
        self.assertNotIn("payload-secret", content)
        self.assertNotIn("result-secret", content)
        self.assertIn('"schemaVersion": "mcp-tool-observability-v1"', content)

    def test_flush_failure_releases_operation_and_emits_failure(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Stdin:
            def write(self, raw):
                pass

            def flush(self):
                raise OSError("flush failed")

        class Child:
            stdin = Stdin()

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call"}\n'
            with self.assertRaisesRegex(RuntimeError, "MCP_PROCESS_FAILED"):
                session.forward(raw, json.loads(raw))

        self.assertEqual(session.operations, {})
        failed = [event for event in writer.events if event[0] == "MCP_TOOL_FAILED"]
        self.assertEqual(len(failed), 1)

    def test_response_and_stop_race_emit_one_terminal_event(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            stdin = io.BytesIO()

            def poll(self):
                return None

            def terminate(self):
                pass

            def wait(self, timeout=None):
                return 0

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call"}\n'
            session.forward(raw, json.loads(raw))
            barrier = threading.Barrier(2)

            def observe():
                barrier.wait()
                session.observe_response({"jsonrpc": "2.0", "id": 1, "result": {}}, generation=session.generation)

            def stop():
                barrier.wait()
                session.stop_child("STDIN_CLOSED")

            threads = [threading.Thread(target=observe), threading.Thread(target=stop)]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()

        terminal_events = [
            event for event in writer.events
            if event[0] in {"MCP_TOOL_RESPONDED", "MCP_TOOL_FAILED", "MCP_TOOL_INTERRUPTED"}
        ]
        self.assertEqual(len(terminal_events), 1)
        self.assertEqual(session.operations, {})

    def test_auth_retry_preserves_operation_identity_without_second_started(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            def __init__(self):
                class Stdin(io.BytesIO):
                    def close(self):
                        pass

                self.stdin = Stdin()

            def poll(self):
                return None

            def terminate(self):
                pass

            def wait(self, timeout=None):
                return 0

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call"}\n'
            failure = {"jsonrpc": "2.0", "id": 1, "error": {"code": -32600, "message": "Authentication is required"}}
            session.forward(raw, json.loads(raw))
            operation_id = next(iter(session.operations.values())).operation_id
            with patch.object(session, "renew_if_needed"):
                self.assertTrue(session.retry_authentication(failure))
            session.observe_response({"jsonrpc": "2.0", "id": 1, "result": {}})

        started = [event for event in writer.events if event[0] == "MCP_TOOL_STARTED"]
        responded = [event for event in writer.events if event[0] == "MCP_TOOL_RESPONDED"]
        self.assertEqual(len(started), 1)
        self.assertEqual(responded[0][1]["operationId"], operation_id)

    def test_stopping_prevents_new_operation_reservation(self) -> None:
        class Writer:
            def write(self, *args, **kwargs):
                pass

        class Child:
            stdin = io.BytesIO()

        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                Writer(),
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            session.stop_requested.set()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call"}\n'
            with self.assertRaisesRegex(RuntimeError, "MCP_INSTANCE_STOPPING"):
                session.forward(raw, json.loads(raw))

        self.assertEqual(session.operations, {})

    def test_stop_between_reservation_and_write_prevents_stdin_write(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        class Child:
            def __init__(self):
                class Stdin(io.BytesIO):
                    def close(self):
                        pass

                self.stdin = Stdin()

            def poll(self):
                return None

            def terminate(self):
                pass

            def wait(self, timeout=None):
                return 0

        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                Writer(),
                runtime.TokenLease("token", time.time() + 3600),
            )
            child = Child()
            session.child = child
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call"}\n'
            emit_event = session._emit_tool_event

            def emit_then_stop(operation, event_type, status, generation, **fields):
                emit_event(operation, event_type, status, generation, **fields)
                if event_type == "MCP_TOOL_STARTED":
                    session.stop_child("STDIN_CLOSED")

            with patch.object(session, "_emit_tool_event", side_effect=emit_then_stop):
                with self.assertRaisesRegex(RuntimeError, "MCP_INSTANCE_STOPPING"):
                    session.forward(raw, json.loads(raw))

        self.assertEqual(child.stdin.getvalue(), b"")
        self.assertEqual(session.operations, {})

    def test_stale_generation_response_does_not_finish_operation(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            stdin = io.BytesIO()

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.generation = 2
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call"}\n'
            session.forward(raw, json.loads(raw))
            session.observe_response({"jsonrpc": "2.0", "id": 1, "result": {}}, generation=1)

        self.assertEqual(len([event for event in writer.events if event[0] == "MCP_TOOL_RESPONDED"]), 0)
        self.assertEqual(len(session.operations), 1)

    def test_write_failure_releases_operation_and_emits_failure(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            class Stdin:
                def write(self, raw):
                    raise BrokenPipeError

                def flush(self):
                    pass

            stdin = Stdin()

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call"}\n'
            with self.assertRaisesRegex(RuntimeError, "MCP_PROCESS_FAILED"):
                session.forward(raw, json.loads(raw))

        self.assertEqual(session.operations, {})
        failed = [event for event in writer.events if event[0] == "MCP_TOOL_FAILED"]
        self.assertEqual(len(failed), 1)
        self.assertEqual(failed[0][1]["reasonCode"], "MCP_PROCESS_FAILED")

    def test_stopping_child_interrupts_active_operations(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            stdin = io.BytesIO()

            def poll(self):
                return None

            def terminate(self):
                pass

            def wait(self, timeout=None):
                return 0

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call"}\n'
            session.forward(raw, json.loads(raw))
            session.stop_child("STDIN_CLOSED")

        interrupted = [event for event in writer.events if event[0] == "MCP_TOOL_INTERRUPTED"]
        self.assertEqual(len(interrupted), 1)
        self.assertEqual(interrupted[0][1]["reasonCode"], "MCP_INSTANCE_STOPPING")

    def test_stale_generation_partial_output_is_discarded(self) -> None:
        class Output:
            def __init__(self):
                self.buffer = io.BytesIO()

            def flush(self):
                pass

        class Child:
            stdout = io.BytesIO(b"stale-partial")

        original_stdout = runtime.sys.stdout
        output = Output()
        runtime.sys.stdout = output
        try:
            with tempfile.TemporaryDirectory() as directory:
                session = runtime.RuntimeSession(
                    {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                    Path(directory),
                    type("Writer", (), {"write": lambda *args, **kwargs: None})(),
                    runtime.TokenLease("token", time.time() + 3600),
                )
                session.generation = 2
                session.relay_stdout(Child(), 1)
        finally:
            runtime.sys.stdout = original_stdout

        self.assertEqual(output.buffer.getvalue(), b"")

    def test_renewal_replays_initialized_notification_after_ready(self) -> None:
        class Child:
            def __init__(self):
                self.stdin = io.BytesIO()

            def poll(self):
                return None

            def terminate(self):
                pass

            def wait(self, timeout=None):
                return 0

        class Writer:
            def write(self, *args, **kwargs):
                pass

        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {
                    "renewal_margin_seconds": "30",
                    "renewal_cooldown_seconds": "10",
                    "minimum_lease_seconds": "5",
                },
                Path(directory),
                Writer(),
                runtime.TokenLease("old", time.time() - 1),
            )
            session.child = Child()
            session.initialize_raw = b'{"jsonrpc":"2.0","id":1,"method":"initialize"}\n'
            session.initialize_id = "1"
            session.initialized_raw = b'{"jsonrpc":"2.0","method":"notifications/initialized"}\n'
            replacement = Child()

            def start_child(replay_initialize=False):
                session.child = replacement
                session.ready = threading.Event()
                session.ready.set()

            with patch.object(runtime, "acquire_token", return_value=runtime.TokenLease("new", time.time() + 60)):
                with patch.object(session, "start_child", side_effect=start_child):
                    session.renew_if_needed()

        self.assertEqual(replacement.stdin.getvalue(), session.initialized_raw)

    def test_transport_authentication_failure_has_one_bounded_retry(self) -> None:
        class Child:
            def __init__(self):
                self.stdin = io.BytesIO()

            def poll(self):
                return None

            def terminate(self):
                pass

            def wait(self, timeout=None):
                return 0

        class Writer:
            def write(self, *args, **kwargs):
                pass

        raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n'
        request = {"jsonrpc": "2.0", "id": 1, "method": "tools/list"}
        failure = {"jsonrpc": "2.0", "id": 1, "error": {"code": -32600, "message": "Authentication is required"}}
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                Writer(),
                runtime.TokenLease("token", time.time() + 3600),
            )
            child = Child()
            session.child = child
            session.forward(raw, request)
            with patch.object(session, "renew_if_needed"):
                self.assertTrue(session.retry_authentication(failure))
                self.assertFalse(session.retry_authentication(failure))

        self.assertEqual(child.stdin.getvalue(), raw + raw)

    def test_expired_lease_fails_closed_when_request_is_in_flight(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() - 1),
            )
            session.initialize_raw = b'{"jsonrpc":"2.0","method":"initialize"}\n'
            session.outstanding["1"] = 1

            with self.assertRaisesRegex(RuntimeError, "MCP_REAUTH_REQUIRED"):
                session.renew_if_needed()

        self.assertEqual(writer.events, [])

    def test_relay_stdin_forwards_small_message_without_waiting_for_eof(self) -> None:
        read_fd, write_fd = os.pipe()
        original_stdin = runtime.sys.stdin
        stdin = io.TextIOWrapper(os.fdopen(read_fd, "rb"), encoding="utf-8")
        runtime.sys.stdin = stdin

        class Child:
            stdin = io.BytesIO()

            def poll(self):
                return None

            def terminate(self):
                pass

            def wait(self, timeout=None):
                return 0

        class Writer:
            def write(self, *args, **kwargs) -> None:
                pass

        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                Writer(),
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            thread = threading.Thread(target=session.relay_stdin)
            thread.start()
            try:
                os.write(write_fd, b'{"jsonrpc":"2.0","method":"initialize"}\n')

                deadline = time.monotonic() + 1
                while Child.stdin.tell() == 0 and time.monotonic() < deadline:
                    time.sleep(0.01)

                self.assertEqual(Child.stdin.getvalue(), b'{"jsonrpc":"2.0","method":"initialize"}\n')
                self.assertTrue(thread.is_alive())
            finally:
                os.close(write_fd)
                thread.join(timeout=1)
                stdin.close()
                runtime.sys.stdin = original_stdin

    def test_redact_removes_transport_secrets(self) -> None:
        value = (
            'Authorization: Bearer abc token=xyz client_secret=secret '
            '"access_token":"access" "refresh_token":"refresh" '
            '"delegation_token":"delegation" "prompt":"private prompt" '
            r'"prompt":"a\"secret"'
        )
        result = runtime.redact(value)

        self.assertNotIn("abc", result)
        self.assertNotIn("xyz", result)
        self.assertNotIn("client_secret=secret", result)
        self.assertNotIn('"access_token":"access"', result)
        self.assertNotIn('"refresh_token":"refresh"', result)
        self.assertNotIn('"delegation_token":"delegation"', result)
        self.assertNotIn("private prompt", result)
        self.assertNotIn(r'a\"secret', result)
        self.assertIn("<redacted>", result)

    def test_event_writer_emits_bounded_structured_event(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            writer = runtime.EventWriter(Path(directory), "instance-1")
            writer.write(
                "TOKEN_ACQUISITION_FAILED",
                severity="ERROR",
                code="TOKEN_ENDPOINT_REJECTED",
                message="secret=hidden",
            )

            event = json.loads((Path(directory) / "events.jsonl").read_text(encoding="utf-8"))
            self.assertEqual(event["instanceId"], "instance-1")
            self.assertEqual(event["event"], "TOKEN_ACQUISITION_FAILED")
            self.assertEqual(event["code"], "TOKEN_ENDPOINT_REJECTED")
            self.assertNotIn("hidden", json.dumps(event))

    def test_process_start_time_returns_current_process_identity(self) -> None:
        start_time = runtime.process_start_time(runtime.os.getpid())

        self.assertTrue(start_time)

    def test_two_outstanding_operations_blocks_renewal_on_auth_failure(self) -> None:
        """renew_if_needed refuses when >1 outstanding; op1 → FAILED/MCP_REAUTH_REQUIRED,
        stop_child then interrupts op2 → INTERRUPTED/MCP_INSTANCE_STOPPING."""

        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            def __init__(self):
                self.stdin = io.BytesIO()

            def poll(self):
                return None

            def terminate(self):
                pass

            def wait(self, timeout=None):
                return 0

        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory),
                writer,
                runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()

            # Establish two simultaneous active operations
            raw1 = b'{"jsonrpc":"2.0","id":1,"method":"tools/call"}\n'
            raw2 = b'{"jsonrpc":"2.0","id":2,"method":"tools/call"}\n'
            session.forward(raw1, json.loads(raw1))
            session.forward(raw2, json.loads(raw2))

            key1 = json.dumps(1, sort_keys=True)
            op1_id = session.outstanding[key1].operation_id

            # Authentication failure arrives for op1
            auth_failure = {
                "jsonrpc": "2.0",
                "id": 1,
                "error": {"code": -32600, "message": "Authentication is required"},
            }
            result = session.retry_authentication(auth_failure)

        # renew_if_needed rejects because two ops are outstanding → returns False
        self.assertFalse(result)
        self.assertTrue(session.stop_requested.is_set())

        # Op1 must have terminated as FAILED/MCP_REAUTH_REQUIRED (not yet stopping when finished)
        failed = [e for e in writer.events if e[0] == "MCP_TOOL_FAILED"]
        self.assertEqual(len(failed), 1)
        self.assertEqual(failed[0][1]["operationId"], op1_id)
        self.assertEqual(failed[0][1]["reasonCode"], "MCP_REAUTH_REQUIRED")

        # stop_child must have interrupted op2 as INTERRUPTED/MCP_INSTANCE_STOPPING
        interrupted = [e for e in writer.events if e[0] == "MCP_TOOL_INTERRUPTED"]
        self.assertEqual(len(interrupted), 1)
        self.assertEqual(interrupted[0][1]["reasonCode"], "MCP_INSTANCE_STOPPING")

        # Exactly two STARTED events (one per original forward call), no extra ones
        started = [e for e in writer.events if e[0] == "MCP_TOOL_STARTED"]
        self.assertEqual(len(started), 2)

        # All operations and reservations must be released
        self.assertEqual(session.operations, {})

    def test_push_session_lifecycle_event_emits_to_session_events_endpoint(self) -> None:
        import time as _time
        posted_urls: list[str] = []
        posted_bodies: list[dict] = []

        class Response:
            def __enter__(self):
                return self
            def __exit__(self, *args):
                return False
            def read(self):
                return b""

        def fake_urlopen(request, timeout=None):
            posted_urls.append(request.full_url)
            posted_bodies.append(json.loads(request.data))
            return Response()

        with patch.object(runtime.urllib.request, "urlopen", side_effect=fake_urlopen):
            pusher = runtime.ToolEventPusher(
                base_url="http://localhost:18080",
                project_slug="devlog-ai",
                instance_id="inst-1",
                token_getter=lambda: "test-token",
                agent_session_id="cc7d159d-a138-4b73-b14e-1e7279d013ab",
                agent_provider="claude-code",
            )
            pusher.push_session_lifecycle_event("SESSION_LIFECYCLE_ENDED")
            deadline = _time.monotonic() + 1
            while not posted_urls and _time.monotonic() < deadline:
                _time.sleep(0.01)

        self.assertEqual(len(posted_urls), 1)
        self.assertIn("/api/v1/mcp/session-events", posted_urls[0])
        body = posted_bodies[0]
        self.assertEqual(body["eventType"], "SESSION_LIFECYCLE_ENDED")
        self.assertEqual(body["agentSessionId"], "cc7d159d-a138-4b73-b14e-1e7279d013ab")
        self.assertEqual(body["projectSlug"], "devlog-ai")
        self.assertEqual(body["agentProvider"], "claude-code")

    def test_push_session_lifecycle_event_does_nothing_without_session_id(self) -> None:
        import time as _time
        called: list[bool] = []

        def fake_urlopen(request, timeout=None):
            called.append(True)
            raise AssertionError("Should not be called")

        with patch.object(runtime.urllib.request, "urlopen", side_effect=fake_urlopen):
            pusher = runtime.ToolEventPusher(
                base_url="http://localhost:18080",
                project_slug="devlog-ai",
                instance_id="inst-1",
                token_getter=lambda: "test-token",
                agent_session_id=None,
                agent_provider=None,
            )
            pusher.push_session_lifecycle_event("SESSION_LIFECYCLE_ENDED")
            _time.sleep(0.05)

        self.assertEqual(called, [])

    def test_push_session_lifecycle_event_does_nothing_without_project_slug(self) -> None:
        import time as _time
        called: list[bool] = []

        def fake_urlopen(request, timeout=None):
            called.append(True)
            raise AssertionError("Should not be called")

        with patch.object(runtime.urllib.request, "urlopen", side_effect=fake_urlopen):
            pusher = runtime.ToolEventPusher(
                base_url="http://localhost:18080",
                project_slug="",
                instance_id="inst-1",
                token_getter=lambda: "test-token",
                agent_session_id="some-session",
                agent_provider=None,
            )
            pusher.push_session_lifecycle_event("SESSION_LIFECYCLE_ENDED")
            _time.sleep(0.05)

        self.assertEqual(called, [])

    def test_push_session_lifecycle_event_returns_joinable_thread(self) -> None:
        import time as _time
        completed: list[bool] = []

        def fake_post(body: bytes) -> None:
            _time.sleep(0.01)
            completed.append(True)

        pusher = runtime.ToolEventPusher(
            base_url="http://localhost:18080",
            project_slug="devlog-ai",
            instance_id="inst-1",
            token_getter=lambda: "token",
            agent_session_id="session-1",
            agent_provider="claude-code",
        )
        with patch.object(pusher, "_post_session_event", side_effect=fake_post):
            t = pusher.push_session_lifecycle_event("SESSION_LIFECYCLE_ENDED")

        self.assertIsNotNone(t)
        t.join(timeout=2)
        self.assertEqual(completed, [True])

    def test_relay_stdin_joins_lifecycle_push_before_returning(self) -> None:
        import io as _io
        import os as _os
        completed: list[bool] = []

        class Writer:
            def write(self, *args, **kwargs) -> None:
                pass

        class Child:
            def __init__(self):
                self.stdin = _io.BytesIO()

            def poll(self):
                return None

            def terminate(self):
                pass

            def wait(self, timeout=None):
                return 0

        read_fd, write_fd = _os.pipe()
        original_stdin = runtime.sys.stdin
        runtime.sys.stdin = _io.TextIOWrapper(_os.fdopen(read_fd, "rb"), encoding="utf-8")

        import time as _time

        def slow_post(body: bytes) -> None:
            _time.sleep(0.05)
            completed.append(True)

        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {
                    "renewal_margin_seconds": "30",
                    "renewal_cooldown_seconds": "10",
                    "project_slug": "devlog-ai",
                    "agent_session_id": "session-relay-test",
                    "agent_provider": "claude-code",
                },
                Path(directory),
                Writer(),
                runtime.TokenLease("token", _time.time() + 3600),
            )
            session.child = Child()
            with patch.object(session.pusher, "_post_session_event", side_effect=slow_post):
                thread = threading.Thread(target=session.relay_stdin)
                thread.start()
                try:
                    _os.close(write_fd)
                    thread.join(timeout=2)
                finally:
                    runtime.sys.stdin.close()
                    runtime.sys.stdin = original_stdin

        self.assertEqual(completed, [True], "lifecycle event push must complete before relay_stdin returns")
