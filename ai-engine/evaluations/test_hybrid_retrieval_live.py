"""Tests for the Story 0206 live-capture evaluation extension of hybrid_retrieval.py."""

from evaluations.hybrid_retrieval import (
    LIVE_CAPTURES_PATH,
    LIVE_PROTOCOL,
    LiveCapture,
    LiveConditionResult,
    evaluate_live_capture,
    load_live_captures,
    run_live,
)


class TestLoadLiveCaptures:
    def test_loads_fixture_scenarios(self) -> None:
        captures = load_live_captures()
        assert len(captures) >= 3

    def test_all_captures_have_correct_protocol(self) -> None:
        captures = load_live_captures()
        for capture in captures:
            assert capture.protocol_version == LIVE_PROTOCOL

    def test_all_captures_have_at_least_one_candidate(self) -> None:
        captures = load_live_captures()
        for capture in captures:
            assert capture.candidate_count >= 1
            assert len(capture.candidates) >= 1

    def test_missing_directory_returns_empty(self, tmp_path) -> None:
        captures = load_live_captures(tmp_path / "nonexistent")
        assert captures == ()

    def test_empty_directory_returns_empty(self, tmp_path) -> None:
        captures = load_live_captures(tmp_path)
        assert captures == ()


class TestEvaluateLiveCapture:
    def _vocabulary_gap_capture(self) -> LiveCapture:
        captures = load_live_captures()
        return next(c for c in captures if c.scenario_id == "vocabulary-gap-adr-dependency-inversion")

    def test_lexical_control_excludes_semantic_candidates(self) -> None:
        capture = self._vocabulary_gap_capture()
        result = evaluate_live_capture(capture, "LEXICAL_CONTROL")
        # ADR-067 is SEMANTIC_RETRIEVAL only — must not appear in LEXICAL_CONTROL selection
        assert "doc:ADR-067@rev-0206-fixture" not in result.selected_references

    def test_hybrid_experiment_includes_semantic_candidate(self) -> None:
        capture = self._vocabulary_gap_capture()
        result = evaluate_live_capture(capture, "HYBRID_EXPERIMENT")
        assert "doc:ADR-067@rev-0206-fixture" in result.selected_references

    def test_lexical_control_recall_lower_than_hybrid_on_vocabulary_gap(self) -> None:
        capture = self._vocabulary_gap_capture()
        lexical = evaluate_live_capture(capture, "LEXICAL_CONTROL")
        hybrid = evaluate_live_capture(capture, "HYBRID_EXPERIMENT")
        assert hybrid.final_recall > lexical.final_recall

    def test_semantic_only_recall_nonzero_for_vocabulary_gap(self) -> None:
        capture = self._vocabulary_gap_capture()
        result = evaluate_live_capture(capture, "HYBRID_EXPERIMENT")
        assert result.semantic_only_recall > 0.0

    def test_condition_result_is_dataclass(self) -> None:
        captures = load_live_captures()
        result = evaluate_live_capture(captures[0], "HYBRID_EXPERIMENT")
        assert isinstance(result, LiveConditionResult)

    def test_as_dict_contains_required_keys(self) -> None:
        captures = load_live_captures()
        d = evaluate_live_capture(captures[0], "HYBRID_EXPERIMENT").as_dict()
        for key in ("scenarioId", "condition", "finalRecall", "noiseRate",
                    "semanticOnlyRecall", "lexicalOnlyRecall"):
            assert key in d


class TestRunLive:
    def test_produces_valid_report(self) -> None:
        report = run_live()
        assert report["protocolVersion"] == "story-0206-live-evaluation/v1"
        assert report["captureCount"] >= 3
        assert len(report["results"]) >= 6

    def test_recommendation_is_valid(self) -> None:
        report = run_live()
        assert report["aggregate"]["recommendation"] in {
            "ENABLE_HYBRID", "LEXICAL_SUFFICIENT", "COMPOSITION_BOTTLENECK"
        }

    def test_hybrid_outperforms_lexical_on_fixture_corpus(self) -> None:
        report = run_live()
        agg = report["aggregate"]
        hybrid_recall = agg["HYBRID_EXPERIMENT"]["finalRecallMean"]
        lexical_recall = agg["LEXICAL_CONTROL"]["finalRecallMean"]
        assert hybrid_recall > lexical_recall

    def test_recommendation_is_enable_hybrid_on_fixture_corpus(self) -> None:
        report = run_live()
        assert report["aggregate"]["recommendation"] == "ENABLE_HYBRID"

    def test_deterministic_replay(self) -> None:
        r1 = run_live()
        r2 = run_live()
        assert r1 == r2

    def test_empty_captures_dir_returns_zero_count(self, tmp_path) -> None:
        report = run_live(tmp_path)
        assert report["captureCount"] == 0
        assert report["results"] == []
