from __future__ import annotations

import base64
import importlib.util
import io
import json
import os
import signal
import tempfile
import threading
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import Mock, patch
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "runtime.py"
SPEC = importlib.util.spec_from_file_location("devlog_runtime", MODULE_PATH)
assert SPEC and SPEC.loader
runtime = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runtime)


class _SignalingLock:
    """Lock wrapper that signals the acquisition attempt before blocking."""

    def __init__(self, lock: threading.Lock, attempted: threading.Event) -> None:
        self._lock = lock
        self._attempted = attempted

    def __enter__(self):
        self._attempted.set()
        self._lock.acquire()
        return self

    def __exit__(self, *exception) -> bool:
        self._lock.release()
        return False


class RuntimeTests(unittest.TestCase):
    def test_delivered_response_releases_request_before_stdout_thread_resumes(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=1) as pool:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "0"},
                Path(directory), Mock(), runtime.TokenLease("old", 0),
            )
            session.pusher = Mock()
            session.child = Mock()
            request = b'{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n'
            response = {"jsonrpc": "2.0", "id": 1, "result": {}}
            session.forward(request, json.loads(request), skip_renewal=True)
            original = session.outstanding["1"]
            on_wire, release = threading.Event(), threading.Event()

            def flush():
                on_wire.set()
                self.assertTrue(release.wait(3))

            output = Mock()
            output.buffer.flush.side_effect = flush
            with patch.object(runtime.sys, "stdout", output), patch.object(session, "_renew") as renew:
                relay = pool.submit(session.write_stdout, b"{}\n", session.generation, response)
                try:
                    self.assertTrue(on_wire.wait(3))
                    self.assertNotIn("1", session.outstanding)
                    self.assertIn(original.operation_id, session.operations)
                    session.forward(request, json.loads(request))
                    renew.assert_called_once()
                    subsequent = session.outstanding["1"]
                    self.assertIsNot(original, subsequent)
                finally:
                    release.set()
                self.assertTrue(relay.result(timeout=3))
                self.assertIs(session.outstanding["1"], subsequent)
            self.assertEqual(original.state, runtime.TOOL_RESPONDED)

    def test_waiting_forward_cannot_use_replacement_after_handshake_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=2) as pool:
            session = runtime.RuntimeSession(
                {"minimum_lease_seconds": "5", "renewal_cooldown_seconds": "0", "renewal_margin_seconds": "30"},
                Path(directory), Mock(), runtime.TokenLease("old", 0),
            )
            session.initialize_raw = b"initialize\n"
            child = Mock()
            request = b'{"jsonrpc":"2.0","id":2,"method":"tools/list"}\n'
            handshaking, waiter, release = (threading.Event() for _ in range(3))
            original_wait = session.renewal_condition.wait

            def handshake():
                handshaking.set()
                self.assertTrue(release.wait(3))
                raise RuntimeError("MCP_HANDSHAKE_FAILED")

            def wait_for_renewal():
                waiter.set()
                return original_wait(3)

            def start_child(replay_initialize=False):
                session.child = child

            with (
                patch.object(session, "start_child", side_effect=start_child),
                patch.object(session, "ensure_ready", side_effect=handshake),
                patch.object(session.renewal_condition, "wait", side_effect=wait_for_renewal),
                patch.object(session, "acquire_token_with_retry", return_value=runtime.TokenLease("new", time.time() + 60)),
            ):
                renewal = pool.submit(session.renew_if_needed, True)
                try:
                    self.assertTrue(handshaking.wait(3))
                    forward = pool.submit(session.forward, request, json.loads(request))
                    self.assertTrue(waiter.wait(3))
                finally:
                    release.set()
                for result in (renewal, forward):
                    with self.assertRaisesRegex(RuntimeError, "MCP_REAUTH_REQUIRED"):
                        result.result(timeout=3)
            child.stdin.write.assert_not_called()
            self.assertFalse(session.ready.is_set())

    def test_finalization_flushes_queued_stdin_failure_before_process_exit(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=1) as pool:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            session.pusher = Mock()
            session.child = child = Mock()
            child.poll.return_value = child.wait.return_value = 143
            child.stdin.write.side_effect = BrokenPipeError()
            request = b'{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n'
            queued, release = threading.Event(), threading.Event()
            actual_flush = session.flush_terminal_events

            def flush():
                if threading.current_thread() is not threading.main_thread() and not session.operations:
                    queued.set()
                    self.assertTrue(release.wait(3))
                actual_flush()

            with patch.object(session, "flush_terminal_events", side_effect=flush), patch.object(runtime, "update_manifest"):
                forward = pool.submit(session.forward, request, json.loads(request), skip_renewal=True)
                try:
                    self.assertTrue(queued.wait(3))
                    session.request_stop()
                    self.assertEqual(runtime.finalize_owned_session(session, child, lambda: False), 143)
                finally:
                    release.set()
                with self.assertRaisesRegex(RuntimeError, "MCP_PROCESS_FAILED"):
                    forward.result(timeout=3)
            order = [call.args[0] for call in session.writer.write.call_args_list]
            self.assertEqual(order.count("MCP_TOOL_FAILED"), 1)
            self.assertLess(order.index("MCP_TOOL_FAILED"), order.index("MCP_PROCESS_EXITED"))

    def test_readiness_event_cannot_be_overtaken_by_child_stop_event(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=2) as pool:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            session.child = child = Mock()
            child.poll.return_value = None
            child.terminate.side_effect = lambda: setattr(child.poll, "return_value", 143)
            recording, release = threading.Event(), threading.Event()
            order = []

            def write(event, **fields):
                if event == "MCP_INSTANCE_READY":
                    recording.set()
                    self.assertTrue(release.wait(3))
                order.append(event)

            session.writer.write.side_effect = write
            readiness = pool.submit(session.publish_ready, "RENEWAL_INITIALIZE_RESPONSE")
            try:
                self.assertTrue(recording.wait(3))
                shutdown = pool.submit(session.stop_child, "STDIN_CLOSED")
                self.assertTrue(session.stop_requested.wait(3))
                child.terminate.assert_not_called()
            finally:
                release.set()
            self.assertTrue(readiness.result(timeout=3))
            shutdown.result(timeout=3)
            self.assertFalse(session.publish_ready("RENEWAL_INITIALIZE_RESPONSE"))
            self.assertEqual(order, ["MCP_INSTANCE_READY", "MCP_INSTANCE_STOPPED"])

    def test_readiness_publication_releases_session_lock_while_child_stop_is_held(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=1) as pool:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            session.child = Mock()
            stop_held, attempted = threading.Lock(), threading.Event()
            stop_held.acquire()
            session.child_stop_lock = _SignalingLock(stop_held, attempted)
            publication = pool.submit(session.publish_ready, "INITIALIZE_RESPONSE")
            try:
                self.assertTrue(attempted.wait(3))
                # A concurrent child reap may hold child_stop_lock. The session
                # lock must stay free so protocol I/O is not suspended.
                self.assertTrue(session.lock.acquire(timeout=1))
                session.lock.release()
                self.assertFalse(publication.done())
            finally:
                stop_held.release()
            self.assertTrue(publication.result(timeout=3))
            self.assertTrue(session.ready.is_set())

    def test_handshake_write_shares_stdin_lock_with_forward(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=1) as pool:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            session.handshake_deadline = time.monotonic() + 10
            held, attempted = threading.Lock(), threading.Event()
            held.acquire()
            session.stdin_write_lock = _SignalingLock(held, attempted)
            stream = Mock()
            write = pool.submit(session.write_child_handshake, stream, b"{}\n")
            try:
                self.assertTrue(attempted.wait(3))
                stream.write.assert_not_called()
            finally:
                held.release()
            write.result(timeout=3)
            stream.write.assert_called_once_with(b"{}\n")
            stream.flush.assert_called_once_with()

    def test_sigint_during_finalization_does_not_abandon_pending_popen(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=1) as pool:
            session = runtime.RuntimeSession(
                {"issuer_uri": "test", "jwks_uri": "test", "actor_client_id": "test", "jar": "test"},
                Path(directory), Mock(), runtime.TokenLease("old", 0),
            )
            old, replacement = Mock(), Mock()
            old.poll.return_value = old.wait.return_value = 143
            replacement.poll.return_value = None
            spawning, release = threading.Event(), threading.Event()
            actual_start, actual_wait = session.start_child, session.spawn_done.wait
            order = []
            session.writer.write.side_effect = lambda event, **fields: order.append(event)
            replacement.wait.side_effect = lambda timeout: order.append("reaped") or 143
            startup = None

            def spawn(*args, **kwargs):
                spawning.set()
                self.assertTrue(release.wait(3))
                return replacement

            def start():
                nonlocal startup
                session.child = old
                startup = pool.submit(actual_start)
                self.assertTrue(spawning.wait(3))

            def supervise(*args):
                session.request_stop()
                return old

            def interrupt_before_wait():
                try:
                    signal.raise_signal(signal.SIGINT)
                finally:
                    release.set()
                self.assertTrue(actual_wait(3))

            previous = signal.getsignal(signal.SIGINT)
            with (
                patch.object(runtime.subprocess, "Popen", side_effect=spawn),
                patch.object(runtime, "supervise_child", side_effect=supervise),
                patch.object(runtime, "update_manifest"),
                patch.object(session, "start_child", side_effect=start),
                patch.object(session, "relay_stdin"),
                patch.object(session.spawn_done, "wait", side_effect=interrupt_before_wait),
            ):
                self.assertEqual(runtime.run_owned_session(session, 42, "start"), 143)
            self.assertIs(signal.getsignal(signal.SIGINT), previous)
            with self.assertRaisesRegex(RuntimeError, "MCP_INSTANCE_STOPPING"):
                startup.result(timeout=3)
            self.assertLess(order.index("reaped"), order.index("MCP_PROCESS_EXITED"))

    def test_renewal_fails_closed_when_either_handshake_write_hits_a_full_pipe(self) -> None:
        for phase in ("initialize", "initialized"):
            with self.subTest(phase=phase), tempfile.TemporaryDirectory() as directory:
                session = runtime.RuntimeSession(
                    {"minimum_lease_seconds": "5", "renewal_cooldown_seconds": "0"},
                    Path(directory), Mock(), runtime.TokenLease("old", 0),
                )
                session.initialize_raw = b'{"jsonrpc":"2.0","id":1,"method":"initialize"}\n'
                session.initialized_raw = b'{"jsonrpc":"2.0","method":"notifications/initialized"}\n'
                child = Mock()
                reader, writer = os.pipe()
                try:
                    with os.fdopen(writer, "wb") as stdin:
                        child.stdin = stdin
                        os.set_blocking(writer, False)
                        while True:
                            try:
                                os.write(writer, b"x" * 4096)
                            except BlockingIOError:
                                break
                        os.set_blocking(writer, True)

                        def start_child(replay_initialize=False):
                            self.assertTrue(replay_initialize)
                            session.child = child
                            session.handshake_deadline = time.monotonic() + 0.03
                            if phase == "initialize":
                                session.write_child_handshake(stdin, session.initialize_raw)
                            else:
                                session.initialize_received.set()

                        with (
                            patch.object(session, "start_child", side_effect=start_child),
                            patch.object(session, "acquire_token_with_retry", return_value=runtime.TokenLease("new", time.time() + 60)),
                            self.assertRaisesRegex(RuntimeError, "MCP_REAUTH_REQUIRED") as failure,
                        ):
                            session.renew_if_needed(force=True)
                        self.assertEqual(str(failure.exception.__cause__), "MCP_HANDSHAKE_FAILED")
                        self.assertTrue(os.get_blocking(writer))
                finally:
                    os.close(reader)
                self.assertFalse(session.ready.is_set())
                self.assertFalse(session.renewal_in_progress)
                session.writer.write.assert_any_call("TOKEN_RENEWAL_FAILED", severity="ERROR", code="MCP_HANDSHAKE_FAILED")

    def test_natural_exit_drains_last_response_before_terminal_log(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=1) as pool:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            session.pusher = Mock()
            session.child = child = Mock()
            child.poll.return_value = child.wait.return_value = 0
            response = b'{"jsonrpc":"2.0","id":1,"result":{}}\n'
            child.stdout.read1.side_effect = [response, b""]
            session.forward(b'{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n',
                            {"jsonrpc": "2.0", "id": 1, "method": "tools/list"}, skip_renewal=True)
            admitted, release = threading.Event(), threading.Event()
            order = []
            session.writer.write.side_effect = lambda event, **fields: order.append(event)
            actual_write = session.write_stdout

            def write_after_exit(payload, generation, message=None):
                admitted.set()
                self.assertTrue(release.wait(3))
                return actual_write(payload, generation, message)

            output = Mock()
            output.buffer = io.BytesIO()
            session.stdout_done.clear()
            with (
                patch.object(runtime, "update_manifest"),
                patch.object(runtime.sys, "stdout", output),
                patch.object(runtime.os, "getppid", return_value=42),
                patch.object(runtime, "process_start_time", return_value="start"),
                patch.object(session, "write_stdout", side_effect=write_after_exit),
            ):
                relay = pool.submit(session.run_stdout_relay, child, session.generation, session.stdout_done)
                try:
                    self.assertTrue(admitted.wait(3))
                    self.assertIs(runtime.supervise_child(session, 42, "start"), child)
                    self.assertTrue(session.stop_requested.is_set())
                    self.assertNotIn("MCP_TOOL_RESPONDED", order)
                finally:
                    release.set()
                self.assertEqual(runtime.finalize_owned_session(session, child, lambda: False), 0)
                relay.result(timeout=3)
            self.assertEqual(output.buffer.getvalue(), response)
            self.assertLess(order.index("MCP_TOOL_RESPONDED"), order.index("MCP_PROCESS_EXITED"))

    def test_finalization_records_interrupted_operation_before_exit(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            session.pusher = Mock()
            session.child = child = Mock()
            child.poll.return_value = child.wait.return_value = 143
            session.forward(b'{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n',
                            {"jsonrpc": "2.0", "id": 1, "method": "tools/list"}, skip_renewal=True)
            session.request_stop()  # The stdin relay has not yet recorded interrupted operations.
            with patch.object(runtime, "update_manifest"):
                self.assertEqual(runtime.finalize_owned_session(session, child, lambda: False), 143)
            order = [call.args[0] for call in session.writer.write.call_args_list]
            self.assertEqual(order.count("MCP_TOOL_INTERRUPTED"), 1)
            self.assertLess(order.index("MCP_TOOL_INTERRUPTED"), order.index("MCP_PROCESS_EXITED"))

    def test_stop_does_not_emit_child_stop_before_start_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=2) as pool:
            session = runtime.RuntimeSession(
                {"issuer_uri": "test", "jwks_uri": "test", "actor_client_id": "test", "jar": "test"},
                Path(directory), Mock(), runtime.TokenLease("test", 0),
            )
            child = Mock()
            child.stdout.read1.return_value = b""
            child.stderr = []
            child.poll.return_value = None
            child.terminate.side_effect = lambda: setattr(child.poll, "return_value", 143)
            manifest, release = threading.Event(), threading.Event()

            def record_manifest(*args, **kwargs):
                manifest.set()
                self.assertTrue(release.wait(3))

            with (
                patch.object(runtime.subprocess, "Popen", return_value=child),
                patch.object(runtime, "process_start_time", return_value="start"),
                patch.object(runtime, "update_manifest", side_effect=record_manifest),
            ):
                startup = pool.submit(session.start_child)
                try:
                    self.assertTrue(manifest.wait(3))
                    shutdown = pool.submit(session.stop_child, "STDIN_CLOSED")
                    self.assertTrue(session.stop_requested.wait(3))
                    child.terminate.assert_not_called()
                finally:
                    release.set()
                startup.result(timeout=3)
                shutdown.result(timeout=3)
            order = [call.args[0] for call in session.writer.write.call_args_list]
            self.assertLess(order.index("MCP_PROCESS_STARTED"), order.index("MCP_INSTANCE_STOPPED"))

    def test_stdout_descriptor_retries_short_writes_without_using_python_buffer(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            descriptor, output = os.pipe()
            with os.fdopen(descriptor, "rb") as reader:
                payload = b'{"jsonrpc":"2.0","id":1,"result":{}}\n'
                actual_write = os.write

                def short_write(fd, data):
                    return actual_write(fd, data[:8])

                with os.fdopen(output, "w") as stdout:
                    with patch.object(runtime.sys, "stdout", stdout), patch.object(runtime.os, "write", side_effect=short_write):
                        session.write_stdout(payload, session.generation)
                self.assertEqual(reader.read(), payload)

    def test_main_waits_for_unpublished_replacement_cleanup_after_stdin_eof(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=2) as pool:
            root = Path(directory)
            writer = Mock()
            session = runtime.RuntimeSession(
                {
                    "minimum_lease_seconds": "5", "renewal_cooldown_seconds": "0",
                    "issuer_uri": "test", "jwks_uri": "test", "actor_client_id": "test", "jar": "test",
                },
                root, writer, runtime.TokenLease("old", 0),
            )
            session.pusher = Mock()
            session.pusher.push_session_lifecycle_event.return_value = None
            old, replacement = Mock(), Mock()
            for child in (old, replacement):
                child.poll.return_value = None
                child.wait.return_value = 143
                child.terminate.side_effect = lambda child=child: setattr(child.poll, "return_value", 143)
            initial, spawning, finalizing, release = (threading.Event() for _ in range(4))
            actual_start, actual_wait = session.start_child, session.spawn_done.wait

            def start(replay_initialize=False):
                if session.child is None:
                    session.child = old
                    initial.set()
                    return
                actual_start(replay_initialize)

            def spawn(*args, **kwargs):
                spawning.set()
                self.assertTrue(release.wait(3))
                return replacement

            def read_eof(_size):
                self.assertTrue(spawning.wait(3))
                return b""

            def await_spawn():
                finalizing.set()
                self.assertTrue(actual_wait(3))

            stdin = Mock()
            stdin.buffer.read1.side_effect = read_eof
            order = []
            writer.write.side_effect = lambda event, **kwargs: order.append(event)

            def reap_replacement(timeout=None):
                order.append("replacement_reaped")
                return 143

            replacement.wait.side_effect = reap_replacement
            actual_relay, relay_done = session.relay_stdin, threading.Event()

            def relay_stdin():
                try:
                    actual_relay()
                finally:
                    relay_done.set()

            with (
                patch.object(runtime, "state_root", return_value=root),
                patch.object(runtime, "EventWriter", return_value=writer),
                patch.object(runtime, "required_configuration", return_value={}),
                patch.object(runtime, "validate_configuration"),
                patch.object(runtime, "write_manifest"),
                patch.object(runtime, "update_manifest"),
                patch.object(runtime, "acquire_token", return_value=session.lease),
                patch.object(runtime, "RuntimeSession", return_value=session),
                patch.object(runtime, "process_start_time", return_value="start"),
                patch.object(runtime.sys, "stdin", stdin),
                patch.object(runtime.subprocess, "Popen", side_effect=spawn),
                patch.object(session, "start_child", side_effect=start),
                patch.object(session, "relay_stdin", side_effect=relay_stdin),
                patch.object(session.spawn_done, "wait", side_effect=await_spawn),
                patch.object(session, "acquire_token_with_retry", return_value=runtime.TokenLease("new", time.time() + 60)),
            ):
                launcher = pool.submit(runtime.main)
                try:
                    self.assertTrue(initial.wait(3))
                    renewal = pool.submit(session.renew_if_needed, True)
                    self.assertTrue(finalizing.wait(3))
                    self.assertFalse(launcher.done())
                    replacement.terminate.assert_not_called()
                finally:
                    release.set()
                with self.assertRaisesRegex(RuntimeError, "MCP_REAUTH_REQUIRED"):
                    renewal.result(timeout=3)
                self.assertEqual(launcher.result(timeout=3), 143)
                self.assertTrue(relay_done.wait(3))

            replacement.terminate.assert_called_once()
            replacement.wait.assert_called_once_with(timeout=5)
            self.assertLess(order.index("replacement_reaped"), order.index("MCP_PROCESS_EXITED"))
            self.assertIs(session.child, old)

    def test_parent_loss_stops_live_child_while_stdout_is_blocked(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=2) as pool:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            session.child = child = Mock()
            child.poll.return_value = None
            child.terminate.side_effect = lambda: setattr(child.poll, "return_value", 143)
            child.wait.return_value = 143
            child.stdout.read1.side_effect = [b'{"jsonrpc":"2.0","id":2,"result":{}}\n', b""]
            writing, release = threading.Event(), threading.Event()
            stdout = Mock()

            def blocked_write(_data):
                writing.set()
                self.assertTrue(release.wait(3))

            stdout.buffer.write.side_effect = blocked_write
            with (
                patch.object(runtime.sys, "stdout", stdout),
                patch.object(runtime.os, "getppid", return_value=43),
            ):
                relay = pool.submit(session.relay_stdout, child, session.generation)
                try:
                    self.assertTrue(writing.wait(3))
                    supervisor = pool.submit(runtime.supervise_child, session, 42, "start")
                    self.assertIs(supervisor.result(timeout=1), child)
                    child.terminate.assert_called_once()
                    child.wait.assert_called_once_with(timeout=5)
                    child.stdin.close.assert_not_called()
                    self.assertFalse(relay.done())
                finally:
                    release.set()
                    relay.result(timeout=3)

    def test_supervisor_detects_exit_while_stdout_is_blocked(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=2) as pool:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            session.child = child = Mock()
            child.poll.return_value = 7
            child.stdout.read1.side_effect = [b'{"jsonrpc":"2.0","id":2,"result":{}}\n', b""]
            writing, release = threading.Event(), threading.Event()
            stdout = Mock()

            def blocked_write(_data):
                writing.set()
                self.assertTrue(release.wait(3))

            stdout.buffer.write.side_effect = blocked_write
            with (
                patch.object(runtime.sys, "stdout", stdout),
                patch.object(runtime.os, "getppid", return_value=42),
                patch.object(runtime, "process_start_time", return_value="start"),
            ):
                relay = pool.submit(session.relay_stdout, child, session.generation)
                try:
                    self.assertTrue(writing.wait(3))
                    supervisor = pool.submit(runtime.supervise_child, session, 42, "start")
                    self.assertIs(supervisor.result(timeout=1), child)
                    self.assertTrue(session.stop_requested.is_set())
                finally:
                    release.set()
                    relay.result(timeout=3)

    def test_parent_loss_prevents_replacement_during_real_renewal(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=2) as pool:
            session = runtime.RuntimeSession(
                {"minimum_lease_seconds": "5", "renewal_cooldown_seconds": "0"},
                Path(directory), Mock(), runtime.TokenLease("old", 0),
            )
            session.child = child = Mock()
            child.poll.return_value = None
            terminated, release = threading.Event(), threading.Event()

            def terminate():
                child.poll.return_value = 143
                terminated.set()

            def wait_child(timeout=None):
                self.assertTrue(release.wait(3))
                return 143

            child.terminate.side_effect = terminate
            child.wait.side_effect = wait_child
            with (
                patch.object(session, "acquire_token_with_retry", return_value=runtime.TokenLease("new", time.time() + 60)),
                patch.object(session, "start_child") as start,
                patch.object(runtime.os, "getppid", return_value=43),
            ):
                renewal = pool.submit(session.renew_if_needed, True)
                try:
                    self.assertTrue(terminated.wait(3))
                    supervisor = pool.submit(runtime.supervise_child, session, 42, "start")
                    self.assertTrue(session.stop_requested.wait(1), "stop must be recorded before child cleanup")
                finally:
                    release.set()
                with self.assertRaisesRegex(RuntimeError, "MCP_REAUTH_REQUIRED"):
                    renewal.result(timeout=3)
                self.assertIs(supervisor.result(timeout=3), child)
                start.assert_not_called()
                child.terminate.assert_called_once()

    def test_renewal_is_not_started_after_terminal_decision(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("old", 0))
            session.request_stop()
            with patch.object(session, "acquire_token_with_retry") as acquire:
                with self.assertRaisesRegex(RuntimeError, "MCP_INSTANCE_STOPPING"):
                    session.renew_if_needed(force=True)
            acquire.assert_not_called()
            session.writer.write.assert_not_called()

    def test_stop_prevents_publication_of_child_spawned_concurrently(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=1) as pool:
            session = runtime.RuntimeSession(
                {"issuer_uri": "test", "jwks_uri": "test", "actor_client_id": "test", "jar": "test"},
                Path(directory), Mock(), runtime.TokenLease("old", 0),
            )
            session.child = old = Mock()
            unpublished = Mock()
            unpublished.poll.return_value = None
            spawning, release = threading.Event(), threading.Event()

            def spawn(*args, **kwargs):
                spawning.set()
                self.assertTrue(release.wait(3))
                return unpublished

            with patch.object(runtime.subprocess, "Popen", side_effect=spawn):
                startup = pool.submit(session.start_child)
                try:
                    self.assertTrue(spawning.wait(3))
                    session.request_stop()
                finally:
                    release.set()
                with self.assertRaisesRegex(RuntimeError, "MCP_INSTANCE_STOPPING"):
                    startup.result(timeout=3)

            self.assertIs(session.child, old)
            unpublished.terminate.assert_called_once()
            unpublished.wait.assert_called_once_with(timeout=5)
            old.terminate.assert_not_called()
            unpublished.stdin.write.assert_not_called()

    def test_supervisor_waits_for_real_renewal_and_handshake(self) -> None:
        with tempfile.TemporaryDirectory() as directory, ThreadPoolExecutor(max_workers=1) as pool:
            session = runtime.RuntimeSession(
                {
                    "minimum_lease_seconds": "5", "renewal_cooldown_seconds": "0",
                    "issuer_uri": "test", "jwks_uri": "test", "actor_client_id": "test", "jar": "test",
                },
                Path(directory), Mock(), runtime.TokenLease("old", 0),
            )
            session.initialize_raw = b'{"jsonrpc":"2.0","id":1,"method":"initialize"}\n'
            session.initialize_id = "1"
            session.initialized_raw = b'{"jsonrpc":"2.0","method":"notifications/initialized"}\n'
            session.child = old = Mock()
            old.poll.return_value = None
            terminated, release, initialized = threading.Event(), threading.Event(), threading.Event()
            replacement = Mock()
            replacement.poll.return_value = None
            replacement.stderr = []
            replacement.stdin = Mock(wraps=io.BytesIO())
            replacement.stdin.flush.side_effect = initialized.set
            def read_initialize(_size):
                self.assertTrue(initialized.wait(3))
                replacement.stdout.read1.side_effect = None
                replacement.stdout.read1.return_value = b""
                return b'{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25"}}\n'

            def terminate():
                old.poll.return_value = 143
                terminated.set()

            def wait_child(timeout=None):
                self.assertTrue(release.wait(3))
                return 143

            replacement.stdout.read1.side_effect = read_initialize
            old.terminate.side_effect = terminate
            old.wait.side_effect = wait_child
            with (
                patch.object(session, "acquire_token_with_retry", return_value=runtime.TokenLease("new", time.time() + 60)),
                patch.object(runtime.subprocess, "Popen", return_value=replacement),
                patch.object(runtime, "update_manifest"),
                patch.object(runtime.os, "getppid", return_value=42),
                patch.object(runtime, "process_start_time", return_value="start"),
            ):
                renewal = pool.submit(session.renew_if_needed, True)

                def complete_after_supervisor_observes_gap(_seconds):
                    self.assertIs(session.child, old)
                    self.assertFalse(session.stop_requested.is_set())
                    release.set()
                    renewal.result(timeout=3)
                    replacement.poll.return_value = 0

                try:
                    self.assertTrue(terminated.wait(3))
                    with patch.object(runtime.time, "sleep", side_effect=complete_after_supervisor_observes_gap):
                        self.assertIs(runtime.supervise_child(session, 42, "start"), replacement)
                finally:
                    release.set()

            self.assertTrue(session.ready.is_set())
            self.assertEqual(replacement.stdin.getvalue(), session.initialize_raw + session.initialized_raw)
            session.writer.write.assert_any_call("TOKEN_RENEWAL_SUCCEEDED")

    def test_supervisor_observes_replacement_after_old_child_exit_during_renewal(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            old = Mock()
            old.poll.return_value = 143
            replacement = Mock()
            replacement.poll.return_value = 0
            session.child = old
            session.renewal_in_progress = True

            # Publish the replacement only after the supervisor has had a chance
            # to decide whether the exited old child is the launcher's final child.
            def complete_renewal(_seconds):
                self.assertIs(session.child, old)
                self.assertFalse(session.stop_requested.is_set())
                with session.lock:
                    session.child = replacement
                    session.renewal_in_progress = False

            with (
                patch.object(runtime.os, "getppid", return_value=42),
                patch.object(runtime, "process_start_time", return_value="start"),
                patch.object(runtime.time, "sleep", side_effect=complete_renewal) as next_iteration,
            ):
                child = runtime.supervise_child(session, 42, "start")

        self.assertIs(child, replacement)
        next_iteration.assert_called_once()

    def test_supervisor_stops_when_parent_identity_changes_during_renewal(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            session.child = old = Mock()
            old.poll.return_value = 143
            session.renewal_in_progress = True

            with (
                patch.object(runtime.os, "getppid", return_value=42),
                patch.object(runtime, "process_start_time", side_effect=["start", "other-start"]),
                patch.object(runtime.time, "sleep") as next_iteration,
            ):
                self.assertIs(runtime.supervise_child(session, 42, "start"), old)

            self.assertTrue(session.stop_requested.is_set())
            next_iteration.assert_called_once()
            session.writer.write.assert_called_once_with(
                "MCP_PARENT_DISAPPEARED", severity="WARN", code="PARENT_PROCESS_GONE"
            )
            with self.assertRaisesRegex(RuntimeError, "MCP_INSTANCE_STOPPING"):
                session.start_child()

    def test_supervisor_honors_requested_stop_while_renewal_is_in_progress(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            session.child = old = Mock()
            old.poll.return_value = 143
            session.renewal_in_progress = True

            def request_stop(_seconds):
                self.assertFalse(session.stop_requested.is_set())
                session.stop_child("STDIN_CLOSED")

            with (
                patch.object(runtime.os, "getppid", return_value=42),
                patch.object(runtime, "process_start_time", return_value="start"),
                patch.object(runtime.time, "sleep", side_effect=request_stop),
            ):
                self.assertIs(runtime.supervise_child(session, 42, "start"), old)

            self.assertTrue(session.stop_requested.is_set())
            with self.assertRaisesRegex(RuntimeError, "MCP_INSTANCE_STOPPING"):
                session.start_child()

    def test_supervisor_exits_when_failed_replacement_clears_renewal(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            session.child = old = Mock()
            old.poll.return_value = 143
            session.renewal_in_progress = True

            def fail_replacement(_seconds):
                self.assertTrue(session.renewal_in_progress)
                with session.lock:
                    session.renewal_in_progress = False

            with (
                patch.object(runtime.os, "getppid", return_value=42),
                patch.object(runtime, "process_start_time", return_value="start"),
                patch.object(runtime.time, "sleep", side_effect=fail_replacement) as next_iteration,
            ):
                self.assertIs(runtime.supervise_child(session, 42, "start"), old)

            self.assertTrue(session.stop_requested.is_set())
            next_iteration.assert_called_once()
            self.assertFalse(session.ready.is_set())

    def test_supervisor_returns_exited_child_without_renewal(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession({}, Path(directory), Mock(), runtime.TokenLease("test", 0))
            session.child = child = Mock()
            child.poll.return_value = 7
            with (
                patch.object(runtime.os, "getppid", return_value=42),
                patch.object(runtime, "process_start_time", return_value="start"),
                patch.object(runtime.time, "sleep", side_effect=AssertionError("unexpected wait")),
            ):
                self.assertIs(runtime.supervise_child(session, 42, "start"), child)

            self.assertTrue(session.stop_requested.is_set())
            session.writer.write.assert_not_called()

    def test_supervisor_exits_after_stdin_renewal_failure(self) -> None:
        for failure_stage in ("acquire", "start", "handshake"):
            with self.subTest(stage=failure_stage), tempfile.TemporaryDirectory() as directory:
                session = runtime.RuntimeSession(
                    {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10", "minimum_lease_seconds": "5"},
                    Path(directory),
                    Mock(),
                    runtime.TokenLease("old", time.time() - 1),
                )
                session.initialize_raw = b'{"jsonrpc":"2.0","id":1,"method":"initialize"}\n'

                old, replacement = Mock(), Mock()
                for child in (old, replacement):
                    child.poll.return_value = None
                    child.terminate.side_effect = lambda child=child: setattr(child.poll, "return_value", 143)
                session.child = old

                def start_child(replay_initialize=False):
                    if failure_stage == "start":
                        raise RuntimeError("MCP_PROCESS_FAILED")
                    session.child = replacement
                    session.ready.clear()

                def relay_once(_seconds):
                    self.assertFalse(session.stop_requested.is_set())
                    session.relay_stdin()

                stdin = Mock()
                stdin.buffer.read1.return_value = b'{"jsonrpc":"2.0","id":2,"method":"tools/list"}\n'
                with (
                    patch.object(runtime.os, "getppid", return_value=42),
                    patch.object(runtime, "process_start_time", return_value="start"),
                    patch.object(runtime.time, "sleep", side_effect=relay_once) as next_iteration,
                    patch.object(runtime.sys, "stdin", stdin),
                    patch.object(
                        session, "acquire_token_with_retry",
                        return_value=runtime.TokenLease("new", time.time() + 60),
                        side_effect=RuntimeError("TOKEN_ENDPOINT_REJECTED") if failure_stage == "acquire" else None,
                    ),
                    patch.object(session, "start_child", side_effect=start_child),
                    patch.object(session, "ensure_ready", side_effect=RuntimeError("MCP_HANDSHAKE_FAILED")),
                ):
                    final_child = runtime.supervise_child(session, 42, "start")

                self.assertIs(final_child, replacement if failure_stage == "handshake" else old)
                next_iteration.assert_called_once()
                self.assertTrue(session.stop_requested.is_set())
                self.assertFalse(session.renewal_in_progress)
                self.assertFalse(session.ready.is_set())
                final_child.terminate.assert_called_once()
                final_child.stdin.write.assert_not_called()
                session.writer.write.assert_any_call("TOKEN_RENEWAL_STARTED")
                self.assertNotIn("TOKEN_RENEWAL_SUCCEEDED", [call.args[0] for call in session.writer.write.call_args_list])

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
        self.assertTrue(session.initialize_received.is_set())
        self.assertFalse(session.ready.is_set())

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

    def test_rate_limit_retry_uses_bounded_metadata_and_keeps_operation_identity(self) -> None:
        class Writer:
            def __init__(self):
                self.events = []

            def write(self, event, **fields):
                self.events.append((event, fields))

        class Child:
            stdin = io.BytesIO()

        message = {
            "jsonrpc": "2.0",
            "id": 1,
            "error": {"data": {"source": "CORE", "category": "RATE_LIMITED", "retryAfterSeconds": 5}},
        }
        with tempfile.TemporaryDirectory() as directory:
            writer = Writer()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory), writer, runtime.TokenLease("token", time.time() + 3600)
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_engineering_context"}}\n'
            session.forward(raw, json.loads(raw))
            operation_id = next(iter(session.operations))
            with patch.object(session.stop_requested, "wait", return_value=False) as wait:
                self.assertTrue(session.retry_rate_limited(message))

        wait.assert_called_once_with(timeout=5.0)
        self.assertEqual(session.operations[operation_id].rate_limit_retry_count, 1)
        self.assertEqual(session.child.stdin.getvalue(), raw + raw)
        self.assertEqual(writer.events[0][0], "MCP_TOOL_STARTED")
        self.assertEqual(writer.events[-1][0], "MCP_RATE_LIMIT_RETRY")

    def test_rate_limit_retry_does_not_renew_bearer(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        class Child:
            stdin = io.BytesIO()

        message = {"jsonrpc": "2.0", "id": 1, "error": {
            "data": {"source": "CORE", "category": "RATE_LIMITED", "retryAfterSeconds": 1}
        }}
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory), Writer(), runtime.TokenLease("token", time.time() + 3600)
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_engineering_context"}}\n'
            session.forward(raw, json.loads(raw))
            with patch.object(session, "renew_if_needed") as renew, patch.object(
                session.stop_requested, "wait", return_value=False
            ):
                self.assertTrue(session.retry_rate_limited(message))

        renew.assert_not_called()

    def test_rate_limit_retry_rejects_non_idempotent_operation(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        class Child:
            stdin = io.BytesIO()

        message = {"jsonrpc": "2.0", "id": 1, "error": {
            "data": {"source": "CORE", "category": "RATE_LIMITED", "retryAfterSeconds": 1}
        }}
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory), Writer(), runtime.TokenLease("token", time.time() + 3600)
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"register_project"}}\n'
            session.forward(raw, json.loads(raw))
            with patch.object(session.stop_requested, "wait") as wait:
                self.assertFalse(session.retry_rate_limited(message))

        wait.assert_not_called()
        self.assertEqual(session.child.stdin.getvalue(), raw)

    def test_rate_limit_retry_falls_back_and_stop_interrupts_wait(self) -> None:
        message = {"error": {"data": {"source": "CORE", "category": "RATE_LIMITED"}}}
        self.assertEqual(runtime.RuntimeSession.rate_limit_retry_delay(message), 1)
        self.assertEqual(
            runtime.RuntimeSession.rate_limit_retry_delay(
                {"error": {"data": {"retryAfterSeconds": 1000}}}
            ),
            60,
        )

    def test_rate_limit_retry_stop_interrupts_without_replay(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        class Child:
            stdin = io.BytesIO()

        message = {"jsonrpc": "2.0", "id": 1, "error": {
            "data": {"source": "CORE", "category": "RATE_LIMITED", "retryAfterSeconds": 5}
        }}
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory), Writer(), runtime.TokenLease("token", time.time() + 3600)
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_engineering_context"}}\n'
            session.forward(raw, json.loads(raw))
            session.stop_requested.set()
            with patch.object(session.stop_requested, "wait", return_value=True):
                self.assertFalse(session.retry_rate_limited(message))

        self.assertEqual(session.child.stdin.getvalue(), raw)
        self.assertNotIn(1, session.outstanding)

    def test_rate_limit_retry_serializes_concurrent_responses(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        class Child:
            stdin = io.BytesIO()

        message = {"jsonrpc": "2.0", "id": 1, "error": {
            "data": {"source": "CORE", "category": "RATE_LIMITED", "retryAfterSeconds": 1}
        }}
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory), Writer(), runtime.TokenLease("token", time.time() + 3600)
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_engineering_context"}}\n'
            session.forward(raw, json.loads(raw))
            entered = threading.Event()
            release = threading.Event()

            def wait(**kwargs):
                entered.set()
                release.wait(timeout=2)
                return False

            first = threading.Thread(target=session.retry_rate_limited, args=(message,))
            with patch.object(session.stop_requested, "wait", side_effect=wait):
                first.start()
                self.assertTrue(entered.wait(timeout=1))
                self.assertTrue(session.retry_rate_limited(message))
                release.set()
                first.join(timeout=2)

        self.assertEqual(session.child.stdin.getvalue(), raw + raw)
        self.assertEqual(len(session.operations), 1)

    def test_second_rate_limit_after_replay_is_terminal(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        class Child:
            stdin = io.BytesIO()

        message = {"jsonrpc": "2.0", "id": 1, "error": {
            "data": {"source": "CORE", "category": "RATE_LIMITED", "retryAfterSeconds": 1}
        }}
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory), Writer(), runtime.TokenLease("token", time.time() + 3600)
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_engineering_context"}}\n'
            session.forward(raw, json.loads(raw))
            with patch.object(session.stop_requested, "wait", return_value=False):
                self.assertTrue(session.retry_rate_limited(message))
            self.assertFalse(session.retry_rate_limited(message))

        self.assertEqual(session.child.stdin.getvalue(), raw + raw)
        self.assertEqual(len(session.operations), 0)

    def test_rate_limit_retry_does_not_replay_after_terminalization(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        class Child:
            stdin = io.BytesIO()

        message = {"jsonrpc": "2.0", "id": 1, "error": {
            "data": {"source": "CORE", "category": "RATE_LIMITED", "retryAfterSeconds": 1}
        }}
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory), Writer(), runtime.TokenLease("token", time.time() + 3600)
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_engineering_context"}}\n'
            session.forward(raw, json.loads(raw))
            operation = next(iter(session.operations.values()))

            def terminalize_then_continue(**kwargs):
                with session.lock:
                    session._finish_operation_locked(operation, runtime.TOOL_FAILED, session.generation)
                return False

            with patch.object(session.stop_requested, "wait", side_effect=terminalize_then_continue):
                self.assertFalse(session.retry_rate_limited(message))

        self.assertEqual(session.child.stdin.getvalue(), raw)
        self.assertEqual(len(session.operations), 0)

    def test_rate_limit_retry_terminalizes_after_generation_change(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        class Child:
            stdin = io.BytesIO()

        message = {"jsonrpc": "2.0", "id": 1, "error": {
            "data": {"source": "CORE", "category": "RATE_LIMITED", "retryAfterSeconds": 1}
        }}
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory), Writer(), runtime.TokenLease("token", time.time() + 3600)
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_engineering_context"}}\n'
            session.forward(raw, json.loads(raw))
            original_generation = session.generation

            def advance_generation(**kwargs):
                session.generation = original_generation + 1
                return False

            with patch.object(session.stop_requested, "wait", side_effect=advance_generation):
                self.assertFalse(session.retry_rate_limited(message))

        self.assertEqual(session.child.stdin.getvalue(), raw)
        self.assertEqual(len(session.operations), 0)

    def test_rate_limit_retry_rejects_non_tool_requests(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        class Child:
            stdin = io.BytesIO()

        message = {"jsonrpc": "2.0", "id": 1, "error": {
            "data": {"source": "CORE", "category": "RATE_LIMITED"}
        }}
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory), Writer(), runtime.TokenLease("token", time.time() + 3600)
            )
            session.child = Child()
            raw = b'{"jsonrpc":"2.0","id":1,"method":"ping"}\n'
            session.forward(raw, json.loads(raw))
            with patch.object(session.stop_requested, "wait") as wait:
                self.assertFalse(session.retry_rate_limited(message))

        wait.assert_not_called()

    def test_structured_jsonrpc_errors_keep_layer_specific_diagnostics(self) -> None:
        for category, expected in (
            ("CORE_AUTHENTICATION", "CORE_AUTHENTICATION_FAILED"),
            ("CORE_AUTHORIZATION", "CORE_AUTHORIZATION_FAILED"),
            ("CORE_CONFLICT", "CORE_CONFLICT"),
            ("DELEGATION_AUTHENTICATION", "DELEGATION_FAILED"),
            ("PROVIDER_AUTHENTICATION", "PROVIDER_FAILED"),
            ("TRANSIENT_INFRASTRUCTURE", "TRANSIENT_INFRASTRUCTURE"),
            ("RATE_LIMITED", "RATE_LIMITED"),
            ("PROTOCOL", "PROTOCOL_INVALID"),
            ("FUNCTIONAL", "FUNCTIONAL_ERROR"),
            ("UNKNOWN", "MCP_JSONRPC_ERROR"),
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

        diagnostic = runtime.RuntimeSession.jsonrpc_diagnostic({
            "error": {"data": {"source": "CORE", "category": "CORE_AUTHORIZATION"}}
        })
        self.assertEqual(diagnostic["code"], "CORE_AUTHORIZATION_FAILED")
        self.assertEqual(diagnostic["action"], "CHECK_PROJECT_ACCESS")
        self.assertNotIn("message", diagnostic)

        explicit = runtime.RuntimeSession.jsonrpc_diagnostic({
            "error": {"data": {
                "source": "CORE",
                "category": "FUNCTIONAL",
                "code": "CORE_RESOURCE_NOT_FOUND",
                "layer": "PROVIDER",
                "action": "EXPOSE_SECRET",
            }}
        })
        self.assertEqual(explicit["code"], "CORE_RESOURCE_NOT_FOUND")
        self.assertEqual(explicit["category"], "FUNCTIONAL")
        self.assertEqual(explicit["layer"], "CORE")
        self.assertEqual(explicit["action"], "CHECK_RESOURCE_IDENTIFIER")

    def test_resilience_matrix_keeps_non_transport_failures_terminal(self) -> None:
        cases = (
            ("CORE_AUTHENTICATION", "CORE_AUTHENTICATION_FAILED"),
            ("CORE_AUTHORIZATION", "CORE_AUTHORIZATION_FAILED"),
            ("DELEGATION_AUTHENTICATION", "DELEGATION_FAILED"),
            ("PROVIDER_AUTHENTICATION", "PROVIDER_FAILED"),
            ("FUNCTIONAL", "FUNCTIONAL_ERROR"),
            ("PROTOCOL", "PROTOCOL_INVALID"),
            ("RATE_LIMITED", "RATE_LIMITED"),
            ("TRANSIENT_INFRASTRUCTURE", "TRANSIENT_INFRASTRUCTURE"),
        )
        for category, expected_code in cases:
            with self.subTest(category=category):
                message = {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "error": {"data": {"source": "MATRIX", "category": category}},
                }
                diagnostic = runtime.RuntimeSession.jsonrpc_diagnostic(message)
                self.assertEqual(diagnostic["code"], expected_code)
                self.assertFalse(diagnostic["retryable"])
                self.assertFalse(runtime.RuntimeSession.is_transport_auth_failure(message))

    def test_resilience_matrix_classifies_token_failures_without_live_endpoint(self) -> None:
        cases = (
            (runtime.urllib.error.HTTPError("https://invalid/token", 502, "bad gateway", {}, None), True),
            (runtime.urllib.error.HTTPError("https://invalid/token", 503, "unavailable", {}, None), True),
            (runtime.urllib.error.HTTPError("https://invalid/token", 429, "rate limited", {}, None), False),
            (runtime.urllib.error.URLError(TimeoutError("timeout")), True),
            (runtime.urllib.error.URLError(ConnectionRefusedError()), False),
        )
        for error, expected_transient in cases:
            with self.subTest(error=type(error).__name__):
                self.assertEqual(
                    runtime.RuntimeSession.is_transient_token_error(error),
                    expected_transient,
                )

    def test_ambiguous_non_idempotent_mutation_is_sent_once(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        class Child:
            def __init__(self):
                self.stdin = io.BytesIO()

        with tempfile.TemporaryDirectory() as directory:
            child = Child()
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory), Writer(), runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = child
            raw = b'{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"register_project"}}\n'
            session.forward(raw, json.loads(raw))
            failure = {"jsonrpc": "2.0", "id": 1, "error": {
                "code": -32600, "message": "Authentication is required"
            }}
            with patch.object(session, "renew_if_needed") as renew:
                self.assertFalse(session.retry_authentication(failure))
                renew.assert_not_called()

        self.assertEqual(child.stdin.getvalue(), raw)

    def test_idempotent_replay_keeps_operation_identity(self) -> None:
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
                Path(directory), Writer(), runtime.TokenLease("token", time.time() + 3600),
            )
            session.writer = writer
            session.child = Child()
            request = {
                "jsonrpc": "2.0", "id": 7, "method": "tools/call",
                "params": {"name": "devlog_story_agent", "arguments": {"idempotencyKey": "matrix-1"}},
            }
            raw = json.dumps(request).encode()
            session.forward(raw, request)
            operation_id = next(iter(session.operations.values())).operation_id
            failure = {"jsonrpc": "2.0", "id": 7, "error": {
                "code": -32600, "message": "Authentication is required"
            }}
            with patch.object(session, "renew_if_needed"):
                self.assertTrue(session.retry_authentication(failure))

        self.assertIn(operation_id, session.operations)
        self.assertEqual(session.operations[operation_id].operation_id, operation_id)

    def test_unknown_event_code_gets_bounded_fallback_diagnostic(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            writer = runtime.EventWriter(Path(directory), "instance-1")
            writer.write("MCP_PROCESS_EXITED", severity="ERROR", code="UNEXPECTED_FAILURE")
            event = json.loads((Path(directory) / "events.jsonl").read_text().strip())

        self.assertEqual(event["code"], "MCP_UNKNOWN_DIAGNOSTIC")
        self.assertEqual(event["category"], "UNKNOWN")
        self.assertFalse(event["retryable"])
        self.assertNotIn("message", event)

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

    def test_story_agent_is_replayed_only_with_caller_idempotency_key(self) -> None:
        class Writer:
            def write(self, event, **fields):
                pass

        class Child:
            stdin = io.BytesIO()

        failure = {"jsonrpc": "2.0", "id": 1, "error": {
            "code": -32600, "message": "Authentication is required"
        }}
        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {"renewal_margin_seconds": "30", "renewal_cooldown_seconds": "10"},
                Path(directory), Writer(), runtime.TokenLease("token", time.time() + 3600),
            )
            session.child = Child()
            without_key = {
                "jsonrpc": "2.0", "id": 1, "method": "tools/call",
                "params": {"name": "devlog_story_agent", "arguments": {}}
            }
            session.forward(json.dumps(without_key).encode(), without_key)
            with patch.object(session, "renew_if_needed") as renew:
                self.assertFalse(session.retry_authentication(failure))
                renew.assert_not_called()

            with_key = {
                "jsonrpc": "2.0", "id": 2, "method": "tools/call",
                "params": {"name": "devlog_story_agent", "arguments": {
                    "idempotencyKey": "story-agent-001"
                }}
            }
            session.forward(json.dumps(with_key).encode(), with_key)
            with patch.object(session, "renew_if_needed"):
                self.assertTrue(session.retry_authentication({**failure, "id": 2}))

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

    def test_tool_event_push_contains_only_bounded_fields(self) -> None:
        class Writer:
            instance_id = "instance-1"

            def write(self, event, **fields):
                pass

        class Child:
            stdin = io.BytesIO()

        pushed = []
        completed = threading.Event()

        def capture(body):
            pushed.append(body)
            completed.set()

        with tempfile.TemporaryDirectory() as directory:
            session = runtime.RuntimeSession(
                {
                    "renewal_margin_seconds": "30",
                    "renewal_cooldown_seconds": "10",
                    "project_slug": "devlog-ai",
                },
                Path(directory), Writer(), runtime.TokenLease("secret-token", time.time() + 3600),
            )
            session.child = Child()
            with patch.object(session.pusher, "_post", side_effect=capture):
                raw = b'{"jsonrpc":"2.0","id":"secret-id","method":"tools/call","params":{"secret":"payload-secret"}}\n'
                session.forward(raw, json.loads(raw))
                session.observe_response(
                    {"jsonrpc": "2.0", "id": "secret-id", "result": {"secret": "result-secret"}},
                    generation=session.generation,
                )
                self.assertTrue(completed.wait(timeout=1))

        body = b"".join(pushed)
        self.assertNotIn(b"secret-token", body)
        self.assertNotIn(b"payload-secret", body)
        self.assertNotIn(b"result-secret", body)
        self.assertNotIn(b"secret-id", body)
        self.assertIn(b"operationId", body)

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
            flush_events = session.flush_terminal_events

            def emit_then_stop():
                flush_events()
                with patch.object(session, "flush_terminal_events", side_effect=flush_events):
                    session.stop_child("STDIN_CLOSED")

            with patch.object(session, "flush_terminal_events", side_effect=emit_then_stop):
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
                session.initialize_received.set()

            with patch.object(runtime, "acquire_token", return_value=runtime.TokenLease("new", time.time() + 60)):
                with patch.object(session, "start_child", side_effect=start_child):
                    session.renew_if_needed()

        self.assertEqual(replacement.stdin.getvalue(), session.initialized_raw)
        self.assertTrue(session.ready.is_set())

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
