"""Capture live Story 0158 treatment and MCP-baseline runs.

The OpenAI call is deliberately executed inside the running AI Engine
container so the configured key is never copied to the host process or logs.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import time
from pathlib import Path
from typing import Any
from urllib.request import Request, urlopen


INTENT = "engineering-story-context-analysis"
DEFAULT_PROJECT = "devlog-ai"
DEFAULT_MCP_JAR = Path(__file__).parents[2] / "mcp-server/target/mcp-server-0.0.1-SNAPSHOT.jar"
DEFAULT_OUTPUT = Path("/tmp/opencode/story0158-live")


def rest_json(
    method: str,
    url: str,
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
) -> dict[str, Any]:
    body = None if payload is None else json.dumps(payload).encode()
    request_headers = {"Content-Type": "application/json"}
    request_headers.update(headers or {})
    request = Request(url, data=body, method=method, headers=request_headers)
    with urlopen(request, timeout=180) as response:
        return json.loads(response.read())


class McpClient:
    def __init__(self, jar: Path):
        self.process = subprocess.Popen(
            ["java", "-jar", str(jar)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
        self.request_id = 0
        self.call("initialize", {
            "protocolVersion": "2025-06-18",
            "capabilities": {},
            "clientInfo": {"name": "story-0158-live-runner", "version": "1.0"},
        })
        self.notify("notifications/initialized", {})

    def notify(self, method: str, params: dict[str, Any]) -> None:
        self._send({"jsonrpc": "2.0", "method": method, "params": params})

    def call(self, method: str, params: dict[str, Any]) -> dict[str, Any]:
        self.request_id += 1
        self._send({"jsonrpc": "2.0", "id": self.request_id, "method": method, "params": params})
        while True:
            line = self.process.stdout.readline()
            if not line:
                raise RuntimeError("MCP process exited before returning a response")
            value = json.loads(line)
            if value.get("id") == self.request_id:
                if "error" in value:
                    raise RuntimeError(json.dumps(value["error"]))
                return value["result"]

    def tool(self, name: str, arguments: dict[str, Any]) -> Any:
        result = self.call("tools/call", {"name": name, "arguments": arguments})
        content = result.get("content", [])
        text = next(item["text"] for item in content if item.get("type") == "text")
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return {"mcpText": text}

    def close(self) -> None:
        self.process.terminate()
        self.process.wait(timeout=10)

    def _send(self, value: dict[str, Any]) -> None:
        self.process.stdin.write(json.dumps(value) + "\n")
        self.process.stdin.flush()


def openai_in_container(question: str, context: Any) -> str:
    code = """
import json, os, sys
from openai import OpenAI
payload = json.load(sys.stdin)
prompt = (
    "Answer the QUESTION using only the MCP CONTEXT. Cite exact evidence references "
    "when making factual claims. If the context is insufficient, say NOT_ESTABLISHED.\\n\\n"
    "QUESTION:\\n" + payload["question"] + "\\n\\nCONTEXT:\\n" + json.dumps(payload["context"], ensure_ascii=False)
)
response = OpenAI().responses.create(
    model=os.environ["LLM_MODEL"],
    input=[
        {"role": "system", "content": "You are a grounded engineering context analyst."},
        {"role": "user", "content": prompt},
    ],
    max_output_tokens=2000,
)
print(response.output_text)
"""
    completed = subprocess.run(
        [
            "docker", "exec", "-i", "devlog-ai-engine", "sh", "-c",
            'export OPENAI_API_KEY="$LLM_API_KEY"; exec python -c "$1"',
            "story0158-runner", code,
        ],
        input=json.dumps({"question": question, "context": context}),
        text=True,
        capture_output=True,
        timeout=180,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(f"OpenAI container runner failed: {completed.stderr[-2000:]}")
    return completed.stdout.strip()


def story_agent_run(project: str, question: str, files: list[str], repetition: int) -> dict[str, Any]:
    key = f"story-0158-live-{repetition}-{abs(hash((question, tuple(files))))}"
    execution = rest_json("POST", f"http://localhost:18080/api/v1/projects/{project}/story-agent", {
        "storyId": None,
        "intent": INTENT,
        "question": question,
        "files": files,
        "guidance": {},
    }, {"Idempotency-Key": key})
    task_id = execution.get("aiTaskId")
    for _ in range(90):
        status = rest_json("GET", f"http://localhost:18080/api/v1/ai-tasks/{task_id}")
        if status.get("status") in {"COMPLETED", "FAILED"}:
            break
        time.sleep(2)
    result = rest_json("GET", f"http://localhost:18080/api/v1/ai/tasks/{task_id}/story-context-analysis")
    return {"execution": execution, "status": status, "result": result, "idempotencyKey": key}


def capture_envelope(
    scenario_id: str,
    question: str,
    condition: str,
    repetition: int,
    payload: dict[str, Any],
    *,
    execution_id: str,
    context_digest: str,
    projection_digest: str | None,
    repository_revision: str,
) -> dict[str, Any]:
    envelope = {
        **{key: value for key, value in payload.items() if key not in {"status", "scenarioId", "question"}},
        "scenarioId": scenario_id,
        "question": question,
        "condition": condition,
        "repetition": repetition,
        "status": "COMPLETED",
        "executionId": execution_id,
        "contextDigest": context_digest,
        "projectionDigest": projection_digest,
        "repositoryRevision": repository_revision,
    }
    if "status" in payload:
        envelope["taskStatus"] = payload["status"]
    encoded = json.dumps(envelope, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
    envelope["captureDigest"] = hashlib.sha256(encoded).hexdigest()
    return envelope


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", default=DEFAULT_PROJECT)
    parser.add_argument("--mcp-jar", type=Path, default=DEFAULT_MCP_JAR)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--repetitions", type=int, default=3)
    parser.add_argument("--baseline-only", action="store_true")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    scenarios = json.loads((Path(__file__).parent / "scenarios/story-0158-specificity-v1/manifest.json").read_text())[
        "scenarios"
    ]
    mcp = McpClient(args.mcp_jar)
    try:
        for scenario in scenarios:
            for repetition in range(1, args.repetitions + 1):
                scenario_id = scenario["scenarioId"]
                question = scenario["question"]
                files = scenario.get("files", [])
                if not args.baseline_only:
                    treatment = story_agent_run(args.project, question, files, repetition)
                    if (
                        treatment["execution"].get("status") != "COMPLETED"
                        or treatment["status"].get("status") != "COMPLETED"
                    ):
                        raise RuntimeError(f"Story Agent execution did not complete: {scenario_id}/r{repetition}")
                    story_result = treatment["result"]
                    story_freshness = story_result.get("freshness", {}).get("sourceRevision", {})
                    story_status = treatment["status"]
                    repository_revision = (
                        story_freshness.get("revision")
                        or story_result.get("contextFreshness", {}).get("sourceRevision", {}).get("revision")
                    )
                    if not repository_revision:
                        raise RuntimeError(f"Story Agent capture has no repository revision: {scenario_id}/r{repetition}")
                    story_capture = capture_envelope(
                        scenario_id, question, "STORY_AGENT", repetition, treatment,
                        execution_id=treatment["execution"]["aiTaskId"],
                        context_digest=story_status["contextDigest"],
                        projection_digest=story_status.get("projectionDigest"),
                        repository_revision=repository_revision,
                    )
                    (args.output / f"{scenario_id}-STORY_AGENT-r{repetition}.json").write_text(
                        json.dumps(story_capture, indent=2), encoding="utf-8"
                    )
                context = mcp.tool("get_engineering_context", {
                    "projectSlug": args.project,
                    "intent": question,
                    "files": files,
                    "storyId": None,
                })
                baseline = openai_in_container(question, context)
                metadata = context.get("metadata", {})
                baseline_capture = capture_envelope(
                    scenario_id, question, "MCP_BASELINE", repetition,
                    {"context": context, "answer": baseline},
                    execution_id=f"mcp-baseline-{scenario_id}-r{repetition}",
                    context_digest=metadata["contextDigest"],
                    projection_digest=None,
                    repository_revision=metadata["freshness"]["repositoryRevision"],
                )
                (args.output / f"{scenario_id}-MCP_BASELINE-r{repetition}.json").write_text(
                    json.dumps(baseline_capture, indent=2),
                    encoding="utf-8",
                )
                print(f"captured {scenario_id} repetition {repetition}", flush=True)
    finally:
        mcp.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
