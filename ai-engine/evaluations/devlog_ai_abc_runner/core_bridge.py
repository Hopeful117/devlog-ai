"""Local, fixed-command bridge from the Q1 runner to Java Core tests."""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
from pathlib import Path
from typing import Any

from .provenance import current_head


class CoreHarnessError(RuntimeError):
    """Raised when the selected Java Core harness cannot produce an artifact."""


_OBSERVATIONS = {
    "Q1-A-DEVLOG-CURRENT-r1",
    "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1",
}
_SAFE_OBSERVATION = re.compile(r"^Q1-[AC]-[A-Z-]+-r1$")


def run_core_harness(
    *, observation_id: str, repository_root: Path, output_path: Path, repository_revision: str
) -> dict[str, Any]:
    """Run exactly one Core integration scenario without a shell or retries."""

    if observation_id not in _OBSERVATIONS or not _SAFE_OBSERVATION.fullmatch(observation_id):
        raise CoreHarnessError(f"unsupported Core observation id: {observation_id}")
    root = repository_root.resolve()
    wrapper = root / "backend" / "mvnw"
    test_class = root / "backend" / "src" / "test" / "java" / "com" / "hopeful117" / "devlogai" / "storycontextanalysis" / "StoryContextCoreIntegrationTest.java"
    if not wrapper.is_file() or not wrapper.stat().st_mode & 0o111:
        raise CoreHarnessError("Java Core Maven wrapper is unavailable or not executable")
    if not test_class.is_file():
        raise CoreHarnessError("Java Core integration harness is unavailable")
    output = output_path.resolve()
    if output.exists():
        raise CoreHarnessError(f"refusing to overwrite Core artifact: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    if current_head(root) != repository_revision:
        raise CoreHarnessError("Core harness worktree HEAD does not match the recorded runtime revision")

    command = [
        str(wrapper), "-pl", "backend", "-am",
        "-Dtest=StoryContextCoreIntegrationTest",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        f"-Dq1.core.observationId={observation_id}",
        f"-Dq1.core.output={output}", "test", "-B",
    ]
    if observation_id in {"Q1-A-DEVLOG-CURRENT-r1", "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1"}:
        command.insert(-2, "-Dq1.core.fullPython=true")
    uv = shutil.which("uv")
    if uv is None:
        raise CoreHarnessError("uv is unavailable for the full-Python C replay")
    environment = {
        # Keep the Maven-launched Python replay independent of the caller's
        # ambient PATH while retaining the installed uv location.
        "PATH": f"{Path(uv).parent}:/usr/local/bin:/usr/bin:/bin",
        "CI": "true",
        "HOME": os.environ.get("HOME", ""),
    }
    completed = subprocess.run(
        command, cwd=root, check=False, capture_output=True, text=True,
        env=environment,
    )
    if completed.returncode != 0:
        raise CoreHarnessError(
            f"Java Core harness failed for {observation_id}: "
            f"{completed.stderr[-4000:] or completed.stdout[-4000:]}"
        )
    if not output.is_file():
        raise CoreHarnessError(f"Java Core harness produced no artifact for {observation_id}")
    try:
        artifact = json.loads(output.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise CoreHarnessError("Java Core artifact is not valid JSON") from error
    if artifact.get("observationId") != observation_id or artifact.get("integrationStatus") != "PASS":
        raise CoreHarnessError("Java Core artifact failed its identity or status contract")
    return artifact
