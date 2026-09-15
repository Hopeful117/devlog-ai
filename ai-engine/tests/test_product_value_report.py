from evaluations.product_value.report import oracle_report


def test_oracle_report_keeps_human_decision_unset():
    benchmark = {"repositoryRevision": "rev", "cases": [{
        "caseId": "CASE-04", "expectedEvidence": ["a"],
        "expectedCausalLinks": [{"id": "CL-01", "from": "A", "to": "B", "strength": "NOT_ESTABLISHED"}],
        "expectedConstraintIds": [], "expectedAffectedComponentIds": [], "expectedAffectedTestIds": [],
    }]}
    report = oracle_report(benchmark, {"artifactResolutions": [{"artifact": "a", "status": "RESOLVED"}]})
    assert "NOT_ESTABLISHED" in report
    assert "TODO: HUMAN DECISION" in report
