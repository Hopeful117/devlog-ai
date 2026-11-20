from __future__ import annotations

import importlib
import json
import sys
import tempfile
import unittest
from pathlib import Path


RUNTIME_DIRECTORY = Path(__file__).parents[1]
if str(RUNTIME_DIRECTORY) not in sys.path:
    sys.path.insert(0, str(RUNTIME_DIRECTORY))
status = importlib.import_module("status")


class StatusTests(unittest.TestCase):
    def test_status_keeps_retry_metadata_and_excludes_normal_stop_from_failures(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            events = [
                {
                    "timestamp": "2026-11-20T00:00:00Z",
                    "event": "TOKEN_RENEWAL_RETRY",
                    "severity": "WARN",
                    "code": "TOKEN_ENDPOINT_TIMEOUT",
                    "attempt": 1,
                    "maxAttempts": 1,
                    "secret": "must-not-be-projected",
                },
                {
                    "timestamp": "2026-11-20T00:00:01Z",
                    "event": "TOKEN_RENEWAL_SUCCEEDED",
                    "severity": "INFO",
                },
                {
                    "timestamp": "2026-11-20T00:00:02Z",
                    "event": "MCP_INSTANCE_STOPPED",
                    "severity": "INFO",
                    "code": "STDIN_CLOSED",
                },
            ]
            (path / "events.jsonl").write_text(
                "".join(json.dumps(event) + "\n" for event in events), encoding="utf-8"
            )

            retry = status.last_renewal_retry(path)
            self.assertEqual(retry["code"], "TOKEN_ENDPOINT_TIMEOUT")
            self.assertEqual(retry["attempt"], 1)
            self.assertNotIn("secret", retry)
            self.assertIsNone(status.last_failure(path))

    def test_status_projects_structured_tool_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "events.jsonl").write_text(
                json.dumps(
                    {
                        "timestamp": "2026-11-20T00:00:00Z",
                        "event": "MCP_TOOL_FAILED",
                        "severity": "ERROR",
                        "reasonCode": "CORE_AUTHENTICATION_FAILED",
                        "operationId": "not-projected",
                        "payload": "not-projected",
                    }
                )
                + "\n",
                encoding="utf-8",
            )

            failure = status.last_failure(path)
            self.assertEqual(failure["reasonCode"], "CORE_AUTHENTICATION_FAILED")
            self.assertNotIn("operationId", failure)
            self.assertNotIn("payload", failure)


if __name__ == "__main__":
    unittest.main()
