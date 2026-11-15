"""Small evaluation-only B/C contract fixture for the frozen DevLog Q1 revision.

This is not a replacement repository tool. It makes the observed B direct Git
semantics and C action/result constraints executable in provider-free tests.
The C search implementation remains intentionally represented as a limitation:
its production path delegates to Core context retrieval and ranking, not direct
Git grep.
"""

from __future__ import annotations

import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any


C_SEARCH_MAX_RESULTS = 20
C_READ_MAX_LINES = 500
C_MAX_BYTES = 64_000
C_MAX_TOKENS = 8_000


class CapabilityFixtureError(ValueError):
    pass


@dataclass(frozen=True)
class PinnedQ1Repository:
    repository: Path
    revision: str

    def __post_init__(self) -> None:
        if not self.repository.is_dir():
            raise CapabilityFixtureError("repository directory is unavailable")
        actual = self._git("rev-parse", "--verify", f"{self.revision}^{{commit}}").strip()
        if actual != self.revision:
            raise CapabilityFixtureError("pinned revision is unavailable")

    def b_search(self, query: str, *, path_prefix: str | None = None, max_matches: int = 20) -> dict[str, Any]:
        if not query:
            raise CapabilityFixtureError("repository search query is required")
        paths = [] if path_prefix is None else [safe_path(path_prefix)]
        output = self._git("grep", "-n", "-I", "-F", query, self.revision, "--", *paths, allow_failure=True)
        matches = []
        for line in output.splitlines():
            prefixed = line.removeprefix(f"{self.revision}:")
            parts = prefixed.split(":", 2)
            if len(parts) != 3 or not parts[1].isdigit():
                continue
            path, line_number, text = parts
            matches.append({"path": path, "line": int(line_number), "text": text})
            if len(matches) >= max_matches:
                break
        return {"operation": "search_repository", "query": query, "matches": matches}

    def b_read(self, path: str, *, start_line: int = 1, end_line: int | None = None) -> dict[str, Any]:
        path = safe_path(path)
        if start_line < 1 or (end_line is not None and end_line < start_line):
            raise CapabilityFixtureError("invalid repository line range")
        content = self._git("show", f"{self.revision}:{path}")
        lines = content.splitlines()
        end = len(lines) if end_line is None else end_line
        return {
            "operation": "read_file",
            "reference": path,
            "path": path,
            "revision": self.revision,
            "startLine": start_line,
            "endLine": end,
            "content": "\n".join(lines[start_line - 1:end]),
        }

    def _git(self, *arguments: str, allow_failure: bool = False) -> str:
        result = subprocess.run(
            ["git", "-C", str(self.repository), *arguments],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode and not allow_failure:
            raise CapabilityFixtureError("pinned repository operation failed")
        return result.stdout


def validate_c_action(
    kind: str,
    *,
    revision: str,
    expected_revision: str,
    path: str | None = None,
    path_prefix: str | None = None,
    allowed_path_prefixes: tuple[str, ...] = (),
    result_limit: int = 1,
    maximum_bytes: int = C_MAX_BYTES,
    maximum_tokens: int = C_MAX_TOKENS,
    start_line: int | None = None,
    end_line: int | None = None,
) -> str | None:
    """Mirror the observable fail-closed C action policy from Core."""

    if revision != expected_revision:
        return "REVISION_MISMATCH"
    if kind not in {"SEARCH_EVIDENCE", "READ_EVIDENCE_RANGE"}:
        return "UNKNOWN_CAPABILITY"
    if result_limit < 1 or result_limit > C_SEARCH_MAX_RESULTS:
        return "CAPABILITY_LIMIT_EXCEEDED"
    if maximum_bytes < 1 or maximum_bytes > C_MAX_BYTES or maximum_tokens < 1 or maximum_tokens > C_MAX_TOKENS:
        return "CAPABILITY_LIMIT_EXCEEDED"
    selected_path = path if kind == "READ_EVIDENCE_RANGE" else path_prefix
    if selected_path is not None and not any(
        selected_path == prefix or selected_path.startswith(prefix + "/")
        for prefix in allowed_path_prefixes
    ):
        return "PATH_OUT_OF_SCOPE"
    if kind == "READ_EVIDENCE_RANGE":
        if path is None or start_line is None or end_line is None or start_line < 1 or end_line < start_line:
            return "INVALID_LOCATOR"
        if end_line - start_line + 1 > C_READ_MAX_LINES:
            return "INVALID_LOCATOR"
    return None


def project_c_read(
    evidence: dict[str, Any],
    *,
    path: str,
    start_line: int,
    end_line: int,
    revision: str,
    maximum_bytes: int = C_MAX_BYTES,
    maximum_tokens: int = C_MAX_TOKENS,
) -> dict[str, Any]:
    """Project an already-authorized C evidence item using Core's bounds."""

    if evidence.get("path") != path or evidence.get("revision") != revision:
        raise CapabilityFixtureError("UNAUTHORIZED_RESULT")
    text = evidence.get("content")
    if not isinstance(text, str):
        raise CapabilityFixtureError("UNSUPPORTED_RESULT")
    lines = text.splitlines()
    selected = "\n".join(lines[start_line - 1:end_line])
    maximum_characters = min(maximum_bytes, maximum_tokens * 4)
    selected = selected[:maximum_characters]
    return {
        "reference": evidence["reference"],
        "path": path,
        "revision": revision,
        "content": selected,
        "startLine": start_line,
        "endLine": end_line,
    }


def safe_path(path: str) -> str:
    if not path or path.startswith("/") or ".." in path or "\\" in path:
        raise CapabilityFixtureError("unsafe repository path")
    return path
