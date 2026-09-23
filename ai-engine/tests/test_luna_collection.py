import json
from types import SimpleNamespace

from evaluations import luna_collection


def test_official_assignments_use_frozen_interleaving():
    assignments = luna_collection.official_assignments()
    assert len(assignments) == 18
    assert [
        (item["questionId"], item["condition"], item["repetition"])
        for item in assignments[:6]
    ] == [
        ("CASE-01-COMPARATIVE", "DEVLOG", 1),
        ("CASE-01-COMPARATIVE", "AGENT_DIRECT", 1),
        ("CASE-03", "DEVLOG", 1),
        ("CASE-03", "AGENT_DIRECT", 1),
        ("CASE-04", "DEVLOG", 1),
        ("CASE-04", "AGENT_DIRECT", 1),
    ]
    assert [item["repetition"] for item in assignments] == [1] * 6 + [2] * 6 + [3] * 6


def test_prompts_keep_condition_isolation():
    manifest = luna_collection.load_manifest()
    question = manifest["questions"][0]
    context = SimpleNamespace(prompt_user="frozen evidence only")
    direct = luna_collection.direct_prompt(question)
    devlog = luna_collection.devlog_prompt(question, context)
    assert "frozen evidence only" not in direct
    assert "frozen evidence only" in devlog
    assert "FROZEN_DEVLOG_CONTEXT" not in direct
    assert "repository" in direct.lower()


def test_usage_and_final_answer_are_read_from_raw_opencode_events():
    events = [
        {"type": "step_finish", "part": {"tokens": {"total": 12, "input": 7, "output": 5, "reasoning": 2, "cache": {"read": 3}}}},
        {"type": "text", "part": {"text": "{\"answerText\":\"ok\"}", "metadata": {"openai": {"phase": "final_answer"}}}},
    ]
    assert luna_collection._usage(events) == {"total": 12, "input": 7, "output": 5, "reasoning": 2, "cacheRead": 3}
    assert luna_collection._parse_answer(luna_collection._final_text(events)) == {"answerText": "ok"}


def test_launcher_waits_without_timeout_and_persists_raw_output(monkeypatch, tmp_path):
    captured = {}

    class Process:
        returncode = 0

        def communicate(self):
            return ('{"type":"step_finish","part":{"tokens":{"total":1}}}\n', "")

    def popen(command, **kwargs):
        captured["command"] = command
        captured["kwargs"] = kwargs
        return Process()

    monkeypatch.setattr(luna_collection.subprocess, "Popen", popen)
    raw_path = tmp_path / "raw.jsonl"
    luna_collection._run_opencode("question", worktree=tmp_path, opencode="opencode", raw_path=raw_path)
    assert "--model" in captured["command"]
    assert luna_collection.MODEL in captured["command"]
    assert "timeout" not in captured["kwargs"]
    assert json.loads(raw_path.read_text().splitlines()[0])["type"] == "step_finish"
