import json

from evaluations.story_context_e2e_v2 import aggregate, capture_digest, load_captures


def _capture(condition, outcome="SUCCESS", repetition=1):
    value = {
        "condition": condition,
        "evaluationOutcome": outcome,
        "repositoryRevision": "a" * 40,
        "repetition": repetition,
        "scenarioId": "scenario",
    }
    value["captureDigest"] = capture_digest(value)
    return value


def test_aggregate_excludes_non_success_from_execution_completeness():
    captures = [
        _capture("DIRECT_CONTEXT_OAUTH", repetition=1),
        _capture("DIRECT_CONTEXT_OAUTH", outcome="NOT_EVALUATED", repetition=2),
    ]
    manifest = {
        "protocolVersion": "story-0210-e2e/v2",
        "evaluationVersion": "story-0210-e2e-v2",
        "targetRevision": "a" * 40,
    }

    result = aggregate(captures, manifest)

    assert result["conditions"]["DIRECT_CONTEXT_OAUTH"]["executionCompleteness"] == 0.5
    assert result["conditions"]["DIRECT_CONTEXT_OAUTH"]["outcomes"] == {
        "NOT_EVALUATED": 1,
        "SUCCESS": 1,
    }


def test_load_captures_validates_the_complete_matrix(tmp_path):
    manifest = {
        "repetitions": 1,
        "scenarios": [{"scenarioId": "scenario"}],
    }
    for condition in ("DIRECT_CONTEXT_OAUTH", "DEVLOG_ASSISTED_OAUTH"):
        capture = _capture(condition)
        (tmp_path / f"{condition}.jsonl").write_text(
            json.dumps(capture) + "\n", encoding="utf-8"
        )

    assert len(load_captures(tmp_path, manifest)) == 2
