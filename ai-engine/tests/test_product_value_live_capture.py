from evaluations.product_value.live_capture import _canonical_evidence


def test_live_normalization_only_maps_exact_paths_and_commit_shas():
    case = {"expectedEvidence": [
        "docs/file.md", "commit:abc123", "commit:def456",
    ]}
    evidence = [
        {"reference": "diff:rev:docs/file.md", "provenance": {"originatingFile": "docs/file.md"}},
        {"reference": "git:repo:abc123", "provenance": {}},
        {"reference": "git:repo:abc1234", "provenance": {}},
        {"reference": "summary mentions def456", "provenance": {}},
    ]
    assert _canonical_evidence(case, evidence, "rev") == ["commit:abc123", "docs/file.md"]
