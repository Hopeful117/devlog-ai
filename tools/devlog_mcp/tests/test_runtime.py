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
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n'
            session.forward(raw, json.loads(raw))
            with self.assertRaisesRegex(RuntimeError, "MCP_PROTOCOL_INVALID"):
                session.forward(raw, json.loads(raw))

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
