from __future__ import annotations

import importlib.util
import io
import json
import os
import tempfile
import threading
import time
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "runtime.py"
SPEC = importlib.util.spec_from_file_location("devlog_runtime", MODULE_PATH)
assert SPEC and SPEC.loader
runtime = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runtime)


class RuntimeTests(unittest.TestCase):
    def test_relay_stdin_forwards_small_message_without_waiting_for_eof(self) -> None:
        read_fd, write_fd = os.pipe()
        original_stdin = runtime.sys.stdin
        stdin = io.TextIOWrapper(os.fdopen(read_fd, "rb"), encoding="utf-8")
        runtime.sys.stdin = stdin

        class Child:
            stdin = io.BytesIO()

        class Writer:
            def write(self, *args, **kwargs) -> None:
                pass

        thread = threading.Thread(target=runtime.relay_stdin, args=(Child(), Writer()))
        try:
            thread.start()
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
        value = "Authorization: Bearer abc token=xyz client_secret=secret"
        result = runtime.redact(value)

        self.assertNotIn("abc", result)
        self.assertNotIn("xyz", result)
        self.assertNotIn("client_secret=secret", result)
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
