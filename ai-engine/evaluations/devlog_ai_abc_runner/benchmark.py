"""Frozen benchmark case inputs shared by every A/B/C condition (Story 0237).

Every condition must receive the exact frozen question of its case and the
same human-reviewed scope. The scope is declared per case in the benchmark and
is deliberately independent of oracle evidence, so no condition receives
oracle material and no condition receives a narrower or wider scope than the
others.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

BENCHMARK_PATH = Path("docs/evaluation/devlog-ai-abc-benchmark-v1.json")
BENCHMARK_ID = "devlog-ai-abc"
_ORACLE_KEYS = {"oracle", "requiredClaims", "requiredEvidenceReferences", "forbiddenClaims", "evidence"}
_PLACEHOLDER_MARKERS = ("bounded live evaluation", "placeholder", "todo")
_MINIMUM_QUESTION_LENGTH = 20


class BenchmarkCaseError(ValueError):
    """Raised when a benchmark case cannot safely drive an evaluation condition."""


@dataclass(frozen=True)
class BenchmarkCase:
    case_id: str
    version: str
    question: str
    scope_files: tuple[str, ...]
    story_number: int | None
    scope_review_status: str

    @property
    def question_sha256(self) -> str:
        return hashlib.sha256(self.question.encode("utf-8")).hexdigest()

    def identity(self) -> dict[str, Any]:
        return {
            "caseId": self.case_id,
            "caseVersion": self.version,
            "questionSha256": self.question_sha256,
            "scopeFiles": list(self.scope_files),
            "storyNumber": self.story_number,
            "scopeReviewStatus": self.scope_review_status,
        }


def load_benchmark(repository_root: Path, path: Path = BENCHMARK_PATH) -> dict[str, Any]:
    try:
        value = json.loads((repository_root / path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise BenchmarkCaseError(f"cannot load benchmark: {path}") from error
    if not isinstance(value, dict) or value.get("benchmarkId") != BENCHMARK_ID:
        raise BenchmarkCaseError("benchmark identity is not devlog-ai-abc")
    if not isinstance(value.get("questions"), list):
        raise BenchmarkCaseError("benchmark questions are missing")
    return value


def load_case(repository_root: Path, case_id: str, path: Path = BENCHMARK_PATH) -> BenchmarkCase:
    benchmark = load_benchmark(repository_root, path)
    matches = [item for item in benchmark["questions"] if isinstance(item, dict) and item.get("caseId") == case_id]
    if len(matches) != 1:
        raise BenchmarkCaseError(f"benchmark must contain exactly one case {case_id}")
    return parse_case(matches[0])


def parse_case(value: Mapping[str, Any]) -> BenchmarkCase:
    if _ORACLE_KEYS.intersection(value):
        raise BenchmarkCaseError("benchmark case must not embed oracle material")
    case_id = value.get("caseId")
    version = value.get("version")
    if not isinstance(case_id, str) or not case_id or not isinstance(version, str) or not version:
        raise BenchmarkCaseError("benchmark case identity is incomplete")
    question = value.get("question")
    validate_question(question)
    scope = value.get("scopeFiles")
    if not isinstance(scope, list) or not scope:
        raise BenchmarkCaseError(f"{case_id} must declare a non-empty scopeFiles list")
    scope_files = tuple(validate_scope_path(item) for item in scope)
    if len(set(scope_files)) != len(scope_files):
        raise BenchmarkCaseError(f"{case_id} scopeFiles contains duplicates")
    story_number = value.get("storyNumber")
    if story_number is not None and (not isinstance(story_number, int) or isinstance(story_number, bool)
                                     or story_number < 1):
        raise BenchmarkCaseError(f"{case_id} storyNumber must be a positive integer or null")
    review = value.get("scopeReviewStatus")
    if review not in {"PROPOSED_HUMAN_REVIEW", "ACCEPTED"}:
        raise BenchmarkCaseError(f"{case_id} scopeReviewStatus is invalid")
    return BenchmarkCase(case_id, version, question, scope_files, story_number, review)


def validate_question(question: Any) -> str:
    if not isinstance(question, str) or len(question.strip()) < _MINIMUM_QUESTION_LENGTH:
        raise BenchmarkCaseError("benchmark question is missing or too short")
    lowered = question.lower()
    if any(marker in lowered for marker in _PLACEHOLDER_MARKERS):
        raise BenchmarkCaseError("benchmark question is a placeholder")
    return question


def validate_scope_path(path: Any) -> str:
    if not isinstance(path, str) or not path or path != path.strip():
        raise BenchmarkCaseError("scope path must be a non-empty trimmed string")
    if path.startswith("/") or path.endswith("/") or "\\" in path or "//" in path:
        raise BenchmarkCaseError(f"scope path is not a normalized relative prefix: {path}")
    if any(part in {"", ".", ".."} for part in path.split("/")):
        raise BenchmarkCaseError(f"scope path is not a normalized relative prefix: {path}")
    return path


def assert_question_fidelity(sent: Any, case: BenchmarkCase) -> None:
    """Fail closed unless the condition input carries the exact frozen question."""

    validate_question(sent)
    if hashlib.sha256(sent.encode("utf-8")).hexdigest() != case.question_sha256:
        raise BenchmarkCaseError(f"{case.case_id} question differs from the frozen benchmark question")


def citable_scope_instruction(case: BenchmarkCase) -> str:
    """Scope instruction for B, identical in content to the A/C scope."""

    return (
        "Investigate and cite only repository paths equal to or under these scope prefixes: "
        + ", ".join(case.scope_files)
        + "."
    )
