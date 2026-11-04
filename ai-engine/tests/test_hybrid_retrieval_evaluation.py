from __future__ import annotations

from evaluations.hybrid_retrieval import (
    CONDITIONS,
    evaluate_condition,
    load_corpus,
    run,
)


def test_story_0177_corpus_is_versioned_and_complete() -> None:
    version, scenarios = load_corpus()

    assert version == "1.0.0"
    assert len(scenarios) == 8
    assert {scenario.scenario_id for scenario in scenarios} == {
        "exact-targeted-compose",
        "semantic-indirect-runtime-wiring",
        "multi-source-architecture",
        "insufficient-evidence",
        "truncated-context",
        "wrong-revision-negative-control",
        "out-of-scope-negative-control",
        "superseded-document-negative-control",
    }


def test_semantic_condition_recovers_indirect_evidence() -> None:
    _, scenarios = load_corpus()
    scenario = next(
        item for item in scenarios if item.scenario_id == "semantic-indirect-runtime-wiring"
    )

    lexical = evaluate_condition(scenario, "LEXICAL_CONTROL")
    semantic = evaluate_condition(scenario, "SEMANTIC_EXPERIMENT")

    assert lexical.final_recall == 0.0
    assert semantic.final_recall == 1.0


def test_hard_filters_reject_wrong_revision_and_out_of_scope_candidates() -> None:
    _, scenarios = load_corpus()
    for scenario_id in (
        "wrong-revision-negative-control",
        "out-of-scope-negative-control",
    ):
        scenario = next(item for item in scenarios if item.scenario_id == scenario_id)
        result = evaluate_condition(scenario, "HYBRID_EXPERIMENT")
        rejected = {item["reason"] for item in result.rejected}

        assert result.final_recall == 1.0
        assert "REVISION_SCOPE_MISMATCH" in rejected or "OUT_OF_SCOPE" in rejected


def test_superseded_document_is_not_selected_by_semantic_similarity() -> None:
    _, scenarios = load_corpus()
    scenario = next(
        item
        for item in scenarios
        if item.scenario_id == "superseded-document-negative-control"
    )

    result = evaluate_condition(scenario, "SEMANTIC_EXPERIMENT")

    assert "doc:ADR-018@rev-0177" not in result.selected_references
    assert result.final_recall == 1.0
    assert any(
        item["reference"] == "doc:ADR-018@rev-0177"
        and item["reason"] == "DOCUMENT_STATUS_EXCLUDED"
        for item in result.rejected
    )
    assert result.grounding_accepted is True


def test_all_conditions_have_stable_digests() -> None:
    _, scenarios = load_corpus()
    for scenario in scenarios:
        for condition in CONDITIONS:
            first = evaluate_condition(scenario, condition)
            second = evaluate_condition(scenario, condition)
            assert first.as_dict() == second.as_dict()


def test_report_separates_conditions_and_preserves_safety_metrics() -> None:
    report = run()

    assert report["protocolVersion"] == "story-0177-hybrid-retrieval/v1"
    assert report["scenarioCount"] == 8
    assert set(report["aggregate"]) == set(CONDITIONS)
    assert report["aggregate"]["SEMANTIC_EXPERIMENT"]["scenarioCount"] == 8
    assert all(
        0.0 <= values["candidateRecallMean"] <= 1.0
        and 0.0 <= values["finalRecallMean"] <= 1.0
        and 0.0 <= values["noiseRateMean"] <= 1.0
        for values in report["aggregate"].values()
    )
    assert all(
        values["groundingAcceptanceRate"] == 1.0
        and values["estimatedContextTokensMean"] >= 0
        for values in report["aggregate"].values()
    )
