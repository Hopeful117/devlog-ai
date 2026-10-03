import json
from pathlib import Path

import pytest

from evaluations.story_context_specificity import (
    SpecificityAssessment,
    SpecificityDimensions,
    RuntimeCapture,
    evaluate_specificity,
    load_manifest,
)


ROOT = Path(__file__).parents[1]
MANIFEST = ROOT / "evaluations/scenarios/story-0158-specificity-v1/manifest.json"


def _assessment(scenario_id, condition, repetition, score=2, *, abstention=False, next_step=True):
    return SpecificityAssessment(
            scenarioId=scenario_id,
            condition=condition,
            repetition=repetition,
            captureDigest="a" * 64,
            questionRepeated=True,
        dimensions=SpecificityDimensions(
            relevance=score, specificity=score, evidenceCoverage=score,
            genericUsefulness=score, abstention=score, nextStepUtility=score,
        ),
        citedEvidence=[] if abstention else ["docker-compose.yml"],
        unknownEvidenceReferences=[],
        fabricatedRelations=0,
        scopeViolation=False,
        trustViolation=False,
        abstentionCorrect=abstention,
        nextStepGrounded=next_step,
    )


def _captures(manifest):
    return {
        (scenario.scenario_id, condition, repetition): RuntimeCapture(
            scenarioId=scenario.scenario_id,
            condition=condition,
            repetition=repetition,
            question=scenario.question,
            status="COMPLETED",
            executionId=f"{condition}-{scenario.scenario_id}-{repetition}",
            contextDigest="b" * 64,
            projectionDigest="c" * 64,
            repositoryRevision="revision",
            captureDigest="a" * 64,
            payload=(
                {"result": {"evidenceReferences": ["docker-compose.yml"]}}
                if condition == "STORY_AGENT"
                else {"answer": "captured", "context": {"evidenceReferences": ["docker-compose.yml"]}}
            ),
        )
        for scenario in manifest.scenarios
        for condition in ("STORY_AGENT", "MCP_BASELINE")
        for repetition in range(1, manifest.repetitions + 1)
    }


def test_manifest_freezes_four_required_story_0158_scenarios():
    manifest = load_manifest(MANIFEST)
    assert len(manifest.scenarios) == 4
    assert {scenario.question_kind for scenario in manifest.scenarios} == {
        "BROAD", "TARGETED", "INSUFFICIENT_EVIDENCE", "TRUNCATED_CONTEXT"
    }
    assert manifest.repetitions == 3
    assert manifest.minimum_total_score == 8
    assert manifest.minimum_specificity_delta == 1


def test_specificity_evaluation_requires_agent_improvement_over_mcp_baseline():
    manifest = load_manifest(MANIFEST)
    assessments = []
    for scenario in manifest.scenarios:
        for repetition in range(1, 4):
            assessments.append(_assessment(
                scenario.scenario_id, "STORY_AGENT", repetition, score=2,
                abstention=scenario.expected_abstention,
            ))
            assessments.append(_assessment(
                scenario.scenario_id, "MCP_BASELINE", repetition, score=1,
                abstention=scenario.expected_abstention,
            ))

    result = evaluate_specificity(manifest, assessments, _captures(manifest))

    assert result.passed is True
    assert all(item.passed for item in result.scenarios)
    assert all(item.specificity_delta == 1 for item in result.scenarios)


def test_specificity_evaluation_rejects_unknown_evidence_even_with_high_score():
    manifest = load_manifest(MANIFEST)
    assessments = []
    for scenario in manifest.scenarios:
        for repetition in range(1, 4):
            for condition in ("STORY_AGENT", "MCP_BASELINE"):
                value = _assessment(
                    scenario.scenario_id, condition, repetition,
                    abstention=scenario.expected_abstention,
                )
                if scenario.scenario_id == "0158-targeted-component":
                    value = value.model_copy(update={"unknown_evidence_references": ["unknown:ref"]})
                assessments.append(value)

    result = evaluate_specificity(manifest, assessments, _captures(manifest))

    assert result.passed is False
    targeted = next(item for item in result.scenarios if item.scenario_id == "0158-targeted-component")
    assert targeted.deterministic_gates_passed is False
    assert targeted.passed is False


def test_specificity_evaluation_rejects_assessment_without_runtime_capture():
    manifest = load_manifest(MANIFEST)
    assessment = _assessment(manifest.scenarios[0].scenario_id, "STORY_AGENT", 1)

    with pytest.raises(ValueError, match="no matching runtime capture"):
        evaluate_specificity(manifest, [assessment], {})
