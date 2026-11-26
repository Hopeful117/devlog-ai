"""Q1 B binding for the unchanged comparative V4 runtime.

The binding changes only runtime inputs and dependency identity. Repository
operations remain implemented by the existing pinned Git adapter, and the
provider is always the deterministic scripted provider used by tests.
"""

from __future__ import annotations

import json
import hashlib
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator, Mapping

from evaluations.comparative_baseline.live_adapters import (
    FROZEN_REPOSITORY_REVISION,
    PinnedGitRepositoryTools,
)
from evaluations.comparative_baseline.collection_runtime import ProviderResponse
from evaluations.comparative_baseline.testing import ScriptedProvider
from evaluations.comparative_baseline_v4 import runtime as v4_runtime
from evaluations.comparative_baseline_v4.protocol import SafetyCeilings, V4Assignment

from .provenance import Q1_REPOSITORY_ID, Q1_SOURCE_REVISION, snapshot_probe, verify_commit


class Q1BBindingError(ValueError):
    """Raised when the Q1 B binding cannot establish its identity."""


Q1_B_CONFIG = Path("docs/evaluation/devlog-ai-q1-b-direct-config-v1.json")
Q1_B_REFERENCE = "backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextSubmissionService.java"
Q1_B_NATIVE_V4_CASE = "Q1"


def load_q1_b_config(repository_root: Path, config_path: Path = Q1_B_CONFIG, *, allow_live: bool = False) -> dict[str, Any]:
    path = repository_root / config_path
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise Q1BBindingError(f"cannot load Q1 B configuration: {config_path}") from error
    if not isinstance(value, dict):
        raise Q1BBindingError("Q1 B configuration must be an object")
    if value.get("repository", {}).get("repositoryId") != Q1_REPOSITORY_ID:
        raise Q1BBindingError("Q1 B repository identity mismatch")
    if value.get("repository", {}).get("revision") != Q1_SOURCE_REVISION:
        raise Q1BBindingError("Q1 B source revision mismatch")
    if value.get("runtime", {}).get("runtime") != "comparative-v4-direct-investigator":
        raise Q1BBindingError("Q1 B runtime identity mismatch")
    historical_manifest = repository_root / value["runtime"]["historicalManifest"]
    if not historical_manifest.is_file():
        raise Q1BBindingError("historical V4 manifest is unavailable")
    manifest_hash = hashlib.sha256(historical_manifest.read_bytes()).hexdigest()
    if manifest_hash != value["runtime"].get("historicalManifestSha256"):
        raise Q1BBindingError("historical V4 manifest identity mismatch")
    if value["runtime"].get("historicalRepositoryRevision") != FROZEN_REPOSITORY_REVISION:
        raise Q1BBindingError("historical V4 repository identity mismatch")
    if value.get("runtime", {}).get("status") != "Q1_B_RUNTIME_INTEGRATION_PASS":
        raise Q1BBindingError("Q1 B runtime status is not connected")
    if value.get("bindingStatus") != "Q1_B_RUNTIME_INTEGRATION_PASS":
        raise Q1BBindingError("Q1 B binding status is not connected")
    if not allow_live and value.get("liveStatus") != "Q1_LIVE_TECHNICALLY_BLOCKED":
        raise Q1BBindingError("Q1 live status must remain blocked")
    return value


class Q1PinnedRepositoryTools:
    """Bind the existing pinned Git tool implementation to the Q1 commit.

    V4 passes its historical repository revision to every tool. The delegate
    still performs all path validation and Git operations; this wrapper only
    translates that legacy protocol field after validating the Q1 binding
    revision, while the delegate's own ``revision`` remains the Q1 commit.
    """

    def __init__(self, repository_root: Path, revision: str = Q1_SOURCE_REVISION) -> None:
        verify_commit(repository_root, revision)
        self.revision = revision
        self._delegate = PinnedGitRepositoryTools(repository_root, revision=revision)
        self.operations: list[dict[str, Any]] = []

    def execute(self, operation: str, arguments: dict[str, Any]) -> dict[str, Any]:
        if arguments.get("repositoryRevision") != self.revision:
            raise Q1BBindingError("Q1 B repository revision mismatch")
        legacy_arguments = {**arguments, "repositoryRevision": FROZEN_REPOSITORY_REVISION}
        result = self._delegate.execute(operation, legacy_arguments)
        self.operations.append({"operation": operation, "arguments": dict(arguments), "result": result})
        return result


def _final_response(request: Any) -> ProviderResponse:
    conversation = request.input_payload.get("conversation", [])
    tool_messages = [item for item in conversation if item.get("role") == "tool"]
    if not tool_messages:
        raise Q1BBindingError("scripted V4 provider did not receive a repository result")
    result = tool_messages[-1].get("content", {})
    content = result.get("content") if isinstance(result, dict) else None
    if not isinstance(content, str) or not content:
        raise Q1BBindingError("repository result did not contain readable snapshot content")
    excerpt = content.splitlines()[0][:400]
    answer = {
        "questionId": Q1_B_NATIVE_V4_CASE,
        "questionVersion": "1.0.0",
        "answerText": "The direct V4 investigation read the authorized Core submission path from the pinned DevLog AI snapshot.",
        "relationshipResult": "NOT_APPLICABLE",
        "abstention": False,
        "claims": [{
            "text": "The V4 direct investigation used repository evidence from the pinned DevLog AI snapshot.",
            "claimType": "FACT",
            "references": [Q1_B_REFERENCE],
        }],
        "evidence": [{
            "reference": Q1_B_REFERENCE,
            "locator": {"kind": "LINE_RANGE", "startLine": 1, "endLine": 40},
            "excerpt": excerpt,
            "role": "DIRECT",
        }],
        "confidence": "MEDIUM",
    }
    return ProviderResponse("FINAL", answer, {"output": answer})


@contextmanager
def _case_runtime_input(question_id: str, question_version: str, question: str,
                        repository_revision: str) -> Iterator[None]:
    """Inject one frozen benchmark case into the unchanged V4 runtime (Story 0237 AC-6)."""

    original = v4_runtime.condition_input

    def condition_input(condition: str, _question_id: str, repetition: int) -> dict[str, Any]:
        value = dict(original(condition, "CASE-03", repetition))
        value["questionId"] = question_id
        value["questionVersion"] = question_version
        value["question"] = question
        value["repositoryRevision"] = repository_revision
        return value

    v4_runtime.condition_input = condition_input
    try:
        yield
    finally:
        v4_runtime.condition_input = original


@contextmanager
def _q1_runtime_input(config: Mapping[str, Any]) -> Iterator[None]:
    with _case_runtime_input("Q1", "1.0.0", config["question"], config["repository"]["revision"]):
        yield


def run_q1_b_binding(repository_root: Path, config_path: Path = Q1_B_CONFIG) -> dict[str, Any]:
    config = load_q1_b_config(repository_root, config_path)
    revision = str(config["repository"]["revision"])
    verify_commit(repository_root, revision)
    source_probe = snapshot_probe(repository_root, revision)
    tools = Q1PinnedRepositoryTools(repository_root, revision)
    provider = ScriptedProvider([
        ProviderResponse(
            "TOOL_CALL",
            {"operation": "read_file", "arguments": {"path": Q1_B_REFERENCE, "startLine": 1, "endLine": 40}},
            {},
        ),
        _final_response,
    ])
    runtime = v4_runtime.V4CollectionRuntime(
        provider,
        ceilings=SafetyCeilings(),
        tool_factory=lambda _assignment: tools,
        run_id="q1-b-binding",
        test_only=True,
    )
    assignment = V4Assignment("q1-b-binding", Q1_B_NATIVE_V4_CASE, "1.0.0", Q1_B_NATIVE_V4_CASE, "AGENT_DIRECT_OPEN", 1)
    with _q1_runtime_input(config):
        native = runtime.run_assignment(assignment)
    native_raw = native["rawOutput"]
    return {
        "executionMode": "INTEGRATION_SIMULATION",
        "integrationStatus": "PASS" if native["executionStatus"] == "COMPLETED" else "FAILED",
        "bindingStatus": "Q1_B_RUNTIME_INTEGRATION_PASS" if native["executionStatus"] == "COMPLETED" else "PARTIAL",
        "sourceStatus": "Q1_B_SOURCE_SNAPSHOT_VERIFIED",
        "nativeOutputStatus": "Q1_B_NATIVE_OUTPUT_CAPTURED" if native["executionStatus"] == "COMPLETED" else "PARTIAL",
        "boundaryDouble": "scripted_provider_with_existing_pinned_git_repository_tools",
        "runtimeIdentity": config["runtime"]["runtime"],
        "runtimeContract": config["runtime"]["runtimeContract"],
        "q1Question": config["question"],
        "q1ObservationId": config["observationId"],
        "sourceSnapshot": source_probe,
        "nativeV4RepositoryRevision": native["experimentIdentity"]["inputs"]["repositoryRevision"],
        "nativeV4Output": native,
        "executionStatus": native["executionStatus"],
        "finalResponse": native_raw.get("finalResponse"),
        "evidence": native_raw.get("evidence", []),
        "toolTrace": native_raw.get("toolTrace", []),
        "resources": native["resources"],
        "repositoryOperations": tools.operations,
        "providerIdentity": "scripted-provider-v4-q1-b-v1",
        "providerCalls": len(provider.requests),
        "simulatedProviderCalls": len(provider.requests),
        "externalProviderCalls": 0,
        "externalNetworkCalls": 0,
        "networkCalls": 0,
    }
