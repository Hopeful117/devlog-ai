import pytest

from evaluations.acceptability import AcceptabilityStatus, classify_acceptability


def _observation(**overrides):
    observation = {
        "technicalStatus": "COMPLETED",
        "semanticEligibility": "SEMANTICALLY_ELIGIBLE",
        "groundingStatus": "GROUNDED",
        "freshness": "FRESH",
        "temporalScope": "CURRENT",
        "revisionPinned": True,
        "expectedEvidenceCount": 2,
        "coveredEvidenceCount": 2,
        "contradictionCount": 0,
        "truncated": False,
        "principalConclusionCovered": True,
        "nextStepPresent": True,
        "limitations": [],
        "metadataComplete": True,
        "abstention": False,
        "unauthorizedEvidenceCount": 0,
        "revisionMismatchCount": 0,
    }
    observation.update(overrides)
    return observation


@pytest.mark.parametrize(
    ("overrides", "expected"),
    [
        ({}, AcceptabilityStatus.ACCEPTABLE),
        ({"limitations": ["verify current branch"]}, AcceptabilityStatus.BOUNDED),
        ({"expectedEvidenceCount": 3}, AcceptabilityStatus.INSUFFICIENT),
        ({"freshness": "UNKNOWN"}, AcceptabilityStatus.STALE_OR_UNKNOWN),
        (
            {"truncated": True, "principalConclusionCovered": False},
            AcceptabilityStatus.INSUFFICIENT,
        ),
    ],
)
def test_classifies_explicit_acceptability_states(overrides, expected):
    result = classify_acceptability(_observation(**overrides))

    assert result["status"] == expected
    assert result["semanticEvaluated"] is True


@pytest.mark.parametrize("technical_status", ["PROVIDER_FAILURE", "NOT_EVALUATED"])
def test_excludes_technical_failures_from_semantic_evaluation(technical_status):
    result = classify_acceptability(_observation(technicalStatus=technical_status))

    assert result == {
        "status": AcceptabilityStatus.NOT_EVALUATED,
        "reason": "TECHNICAL_RESULT_NOT_EVALUABLE",
        "semanticEvaluated": False,
    }


def test_historical_revision_pinned_response_is_not_stale():
    result = classify_acceptability(
        _observation(freshness="UNKNOWN", temporalScope="HISTORICAL")
    )

    assert result["status"] == AcceptabilityStatus.ACCEPTABLE


def test_grounding_review_is_bounded_and_contradiction_is_insufficient():
    assert classify_acceptability(_observation(groundingStatus="REVIEW_REQUIRED"))["status"] == AcceptabilityStatus.BOUNDED
    assert classify_acceptability(_observation(contradictionCount=1))["status"] == AcceptabilityStatus.INSUFFICIENT


def test_explicit_evaluation_failure_is_not_mistaken_for_semantic_failure():
    result = classify_acceptability(_observation(evaluationFailure=True))

    assert result["status"] == AcceptabilityStatus.FAILED
    assert result["semanticEvaluated"] is False


def test_missing_metadata_fails_closed():
    observation = _observation(metadataComplete=False)

    result = classify_acceptability(observation)

    assert result["status"] == AcceptabilityStatus.NOT_EVALUATED
    assert result["reason"] == "ACCEPTABILITY_METADATA_INCOMPLETE"


def test_abstention_and_evidence_scope_inconsistency_are_not_acceptable():
    assert classify_acceptability(_observation(abstention=True))["status"] == AcceptabilityStatus.INSUFFICIENT
    assert classify_acceptability(_observation(revisionMismatchCount=1))["status"] == AcceptabilityStatus.INSUFFICIENT
    assert classify_acceptability(_observation(unauthorizedEvidenceCount=1))["status"] == AcceptabilityStatus.INSUFFICIENT
