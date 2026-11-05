import unittest

from story_context_runtime_report import (
    capture_fields_valid,
    readiness_failures,
    readiness_report,
    summarize,
    valid_row,
    _fixture_digest,
    _telemetry_digest,
)


REVISION = "a" * 40


def valid_capture(task_id: str = "task-1") -> dict:
    digest = "b" * 64
    ledger_digest = "c" * 64
    counts = {
        key: 0 for key in (
            "candidateCount", "uniqueCandidateCount", "duplicateCandidateCount",
            "rankedCount", "selectedCount", "enrichedCount", "projectedCount",
            "groundedCount", "providerVisibleCount", "discardedCount",
        )
    }
    payload = {
        "taskId": task_id,
        "scenario": "0158-broad-question",
        "repetition": 1,
        "status": "COMPLETED",
        "elapsedMs": 10,
        "expectedContextBudget": 6000,
        "observedContextBudget": 6000,
        "analysisAvailable": True,
        "confidence": "HIGH",
        "contextDigest": digest,
        "projectionDigest": digest,
        "freshness": {
            "state": "FRESH",
            "sourceRevision": {
                "kind": "PROJECT_REVISION",
                "project": "devlog-ai",
                "revision": REVISION,
            },
        },
        "snapshotBudget": {"providerBudgetAccounting": {"preflightOutcome": "AUTHORIZED"}},
        "lossAccounting": {
            "version": "story-context-loss-ledger-v1",
            "digest": ledger_digest,
            "maximumDetailEntries": 256,
            "detailOverflowCount": 0,
            "counts": counts,
            "lossReasons": {},
            "budgets": {},
            "projection": {"projectionDigest": digest, "warnings": []},
            "providerOutcome": {
                "status": "COMPLETED",
                "visibility": "UNKNOWN",
                "outcomeSource": "AI_INTERACTION_TRACE",
            },
            "evaluationTelemetry": {
                "version": "story-context-evaluation-telemetry-v1",
                    "digest": "dae56c20cd2d698010cf2c3c8bbe0dcfab4bad5e69fdf6b1a5dbe3a255703c3b",
                "stageCounts": {},
                "trustCounts": {},
                    "requestedFileOutcomes": [],
                    "candidateFixture": {
                        "version": "story-context-candidate-fixture-v1",
                        "taskId": task_id,
                        "targetRevision": REVISION,
                        "contextDigest": digest,
                        "projectionDigest": digest,
                        "digest": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                        "candidates": [],
                    },
                "grounding": {
                    "authorizedCount": 0,
                    "projectedCount": 0,
                    "projectedGroundedCount": 0,
                },
            },
        },
        "readiness": {"status": "READY", "failureCodes": []},
        "validCapture": True,
    }
    payload["lossAccounting"]["evaluationTelemetry"]["digest"] = _telemetry_digest(
        payload["lossAccounting"]["evaluationTelemetry"]
    )
    return payload


class RuntimeReadinessTest(unittest.TestCase):
    def test_rejects_revision_and_budget_mismatch(self):
        row = valid_capture()
        row["observedContextBudget"] = 32000
        failures = readiness_failures(row, "d" * 40)
        self.assertIn("TARGET_REVISION_MISMATCH", failures)
        self.assertIn("CONTEXT_BUDGET_MISMATCH", failures)

    def test_reports_duplicate_task_as_readiness_failure(self):
        first = valid_capture()
        second = valid_capture()
        second["repetition"] = 2
        report = readiness_report([first, second], REVISION)
        self.assertFalse(report["campaignEvaluable"])
        self.assertEqual(report["failureReasons"]["TASK_ID_REUSED"], 1)

    def test_valid_capture_remains_evaluable(self):
        row = valid_capture()
        self.assertTrue(capture_fields_valid(row, REVISION))
        self.assertTrue(valid_row(row, REVISION))
        self.assertTrue(summarize([row], REVISION)["singleRevision"])

    def test_accepts_unvalidated_candidate_fixture_marker(self):
        row = valid_capture()
        candidate = {
            "reference": "proposal",
            "kind": "VALIDATABLE_PROPOSAL",
            "path": None,
            "score": 1,
            "trust": "UNVALIDATED",
            "grounded": False,
            "selected": False,
            "projected": False,
            "reason": "INSUFFICIENT_RELEVANCE",
        }
        fixture = row["lossAccounting"]["evaluationTelemetry"]["candidateFixture"]
        fixture["candidates"] = [candidate]
        fixture["digest"] = _fixture_digest(fixture)
        row["lossAccounting"]["counts"].update({
            "candidateCount": 1,
            "uniqueCandidateCount": 1,
            "rankedCount": 1,
            "discardedCount": 1,
        })
        row["lossAccounting"]["evaluationTelemetry"]["digest"] = _telemetry_digest(
            row["lossAccounting"]["evaluationTelemetry"]
        )
        self.assertTrue(capture_fields_valid(row, REVISION))

    def test_malformed_nested_input_becomes_bounded_failure(self):
        row = valid_capture()
        row["freshness"] = []
        report = readiness_report([row], REVISION)
        self.assertFalse(report["campaignEvaluable"])
        self.assertIn("TARGET_REVISION_MISMATCH", report["failureReasons"])

    def test_malformed_provider_cannot_make_campaign_evaluable(self):
        row = valid_capture()
        row["lossAccounting"]["providerOutcome"]["status"] = "UNSUPPORTED"
        report = readiness_report([row], REVISION)
        self.assertFalse(report["campaignEvaluable"])
        self.assertIn("CAPTURE_CONTRACT_INVALID", report["failureReasons"])

    def test_missing_ledger_and_provider_are_classified(self):
        row = valid_capture()
        row.pop("lossAccounting")
        report = readiness_report([row], REVISION)
        self.assertFalse(report["campaignEvaluable"])
        self.assertIn("LOSS_LEDGER_MISSING_OR_INVALID", report["failureReasons"])
        self.assertIn("PROVIDER_OUTCOME_MISSING", report["failureReasons"])

    def test_duplicate_scenario_repetition_is_classified(self):
        first = valid_capture("task-1")
        second = valid_capture("task-2")
        report = readiness_report([first, second], REVISION)
        self.assertIn("SCENARIO_REPETITION_DUPLICATED", report["failureReasons"])

    def test_readiness_rows_are_bounded_and_excluded_from_aggregate(self):
        rows = [valid_capture(f"task-{index}") for index in range(300)]
        report = readiness_report(rows, REVISION)
        self.assertLessEqual(len(report["rows"]), 256)
        rows[0]["readiness"] = {"status": "NOT_READY", "failureCodes": ["TEST"]}
        rows[0]["validCapture"] = False
        aggregate = summarize([rows[0]], REVISION)
        self.assertEqual(aggregate["validCaptureCount"], 0)

    def test_invalid_target_revision_is_rejected(self):
        row = valid_capture()
        report = readiness_report([row], "not-a-revision")
        self.assertFalse(report["campaignEvaluable"])
        self.assertIn("TARGET_REVISION_INVALID", report["failureReasons"])

    def test_unhashable_identity_values_are_bounded_failures(self):
        row = valid_capture()
        row["scenario"] = []
        row["repetition"] = {}
        report = readiness_report([row], REVISION)
        self.assertFalse(report["campaignEvaluable"])
        self.assertIn("SCENARIO_REPETITION_INVALID", report["failureReasons"])


if __name__ == "__main__":
    unittest.main()
