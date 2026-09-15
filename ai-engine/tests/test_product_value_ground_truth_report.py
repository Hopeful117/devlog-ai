from evaluations.product_value.ground_truth_report import render


def test_report_keeps_repository_and_devlog_truth_separate(tmp_path):
    benchmark = {"suiteVersion": "1", "project": "p", "projectId": "p", "repositoryId": "r", "cases": [{
        "caseId": "CASE-04", "expectedEvidence": ["a"], "expectedConstraints": [], "expectedConstraintIds": [],
        "expectedAffectedComponents": [], "expectedAffectedComponentIds": [], "expectedAffectedTests": [], "expectedAffectedTestIds": [],
        "expectedCausalLinks": [{"id": "CL-01", "from": "ADR-043", "to": "ExecutionConfiguration", "strength": "NOT_ESTABLISHED"}],
    }]}
    ground = {"repositoryRevision": "rev", "expectedArtifactCount": 1, "resolvedArtifactCount": 1, "unresolvedArtifactCount": 0, "artifactResolutions": [{"artifact": "a", "status": "RESOLVED"}]}
    comparison = {"rows": [{"artifact": "a", "devlogRetrieval": "NOT_FOUND", "interpretation": "TRUE_BENCHMARK_EVIDENCE_MISSED_BY_DEVLOG"}]}
    text = render(benchmark, ground, comparison, tmp_path)
    assert "TRUE_BENCHMARK_EVIDENCE_MISSED_BY_DEVLOG" in text
    assert "Human decision: `TODO`" in text
