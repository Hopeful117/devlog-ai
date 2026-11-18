import copy
import json
from pathlib import Path

import pytest

from evaluations.evaluator import evaluate_replay
from evaluations.loader import load_scenario
from evaluations.models import EvaluationReplay, GroundingQuality, OverallQuality


SCENARIO_ID = "engineering-story-context-analysis-v1"
SCENARIO_DIR = Path(__file__).parents[1] / "evaluations" / "scenarios" / SCENARIO_ID


def _replay_data() -> dict:
    return json.loads((SCENARIO_DIR / "replay.json").read_text(encoding="utf-8"))


def _quality_fixtures() -> list[dict]:
    return json.loads(
        (SCENARIO_DIR / "response-quality-fixtures.json").read_text(encoding="utf-8")
    )["cases"]


@pytest.mark.parametrize("fixture", _quality_fixtures(), ids=lambda fixture: fixture["name"])
def test_story_context_response_quality_fixtures(fixture: dict) -> None:
    scenario = load_scenario(SCENARIO_ID)
    replay_data = _replay_data()
    analysis = replay_data["output"]["analysisResult"]
    mutation = fixture["mutation"]
    if mutation == "removeObjectiveGrounding":
        analysis["objectiveUnderstanding"].pop("grounding")
    elif mutation == "replaceObjectiveReference":
        analysis["objectiveUnderstanding"]["grounding"]["evidenceReferences"][0][
            "reference"
        ] = "not-authorized"
    elif mutation == "truncateObjectiveSummary":
        analysis["objectiveUnderstanding"]["summary"] = "Truncated context; evidence is not established."
        analysis["objectiveUnderstanding"].pop("grounding")
    elif mutation == "removeFindingEvidence":
        analysis["evidenceFindings"][0]["grounding"]["evidenceReferences"] = []
    elif mutation == "removeClassificationEntry":
        analysis["outputClassification"]["entries"].pop()
    elif mutation == "duplicateClassificationEntry":
        entries = analysis["outputClassification"]["entries"]
        entries.append(copy.deepcopy(entries[0]))

    result = evaluate_replay(scenario, EvaluationReplay.model_validate(replay_data))

    assert result.deterministic_grounding_passed is fixture["expectedDeterministicGrounding"]
    assert set(result.response_quality_dimensions) == {
        "architectureDecisionUnderstanding",
        "componentHistoryReconstruction",
        "mandatoryConstraintPreservation",
        "currentHistoricalDecisionDistinction",
        "complementaryEvidenceSources",
        "semanticCompleteness",
    }
    assert result.response_quality_dimensions["architectureDecisionUnderstanding"] is True
    assert result.response_quality_dimensions["semanticCompleteness"] is False
    if not fixture["expectedDeterministicGrounding"]:
        assert result.grounding_quality == GroundingQuality.UNSUPPORTED
        assert result.overall_quality == OverallQuality.WEAK


def test_story_context_replay_rejects_unauthorized_objective_reference() -> None:
    scenario = load_scenario(SCENARIO_ID)
    replay_data = _replay_data()
    replay_data["output"]["analysisResult"]["objectiveUnderstanding"]["grounding"][
        "evidenceReferences"
    ][0]["reference"] = "not-authorized"

    result = evaluate_replay(
        scenario, EvaluationReplay.model_validate(copy.deepcopy(replay_data))
    )

    assert result.deterministic_grounding_passed is False
    assert result.overall_quality == OverallQuality.WEAK
