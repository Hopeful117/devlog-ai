"""Provider-free integrity checks for the proposed DevLog AI A/B/C corpus."""

import json
import hashlib
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BENCHMARK = ROOT / "docs/evaluation/devlog-ai-abc-benchmark-v1.json"
ORACLES = ROOT / "docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json"
FIRST_TEST = ROOT / "docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json"
NORMALIZATION_CONTRACT = ROOT / "docs/evaluation/devlog-ai-abc-normalized-answer-contract-v1.md"
ORACLE_REVIEW = ROOT / "docs/evaluation/devlog-ai-abc-oracle-human-review-sheets-v1.md"
RUNNER = ROOT / "ai-engine/evaluations/devlog_ai_abc_runner"


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def test_devlog_corpus_has_eight_versioned_cases_and_no_trading_os() -> None:
    benchmark = load(BENCHMARK)
    assert benchmark["status"] == "PROPOSED_HUMAN_REVIEW"
    assert benchmark["repositoryId"] == "git:github.com/Hopeful117/devlog-ai"
    assert benchmark["repositoryRevision"] == "0673d1d32433cdaff054d5f1defd1bedd01c0039"
    assert [case["caseId"] for case in benchmark["questions"]] == [f"Q{i}" for i in range(1, 9)]
    assert all(case["version"] == "1.0.0" for case in benchmark["questions"])
    assert all("Trading OS" not in case["question"] for case in benchmark["questions"])


def test_each_oracle_is_pinned_and_references_existing_repository_paths() -> None:
    benchmark = load(BENCHMARK)
    oracles = load(ORACLES)
    assert oracles["repositoryRevision"] == benchmark["repositoryRevision"]
    assert len(oracles["oracles"]) == 8
    assert {oracle["caseId"] for oracle in oracles["oracles"]} == {f"Q{i}" for i in range(1, 9)}
    for oracle in oracles["oracles"]:
        assert oracle["version"] == ("1.1.0" if oracle["caseId"] == "Q1" else "1.0.0")
        assert oracle["requiredClaims"]
        assert oracle["requiredEvidenceReferences"]
        assert oracle["expectedAnswerClass"]
        for reference in oracle["requiredEvidenceReferences"]:
            assert_reference_resolves(reference)
        for claim in oracle["requiredClaims"]:
            assert claim["status"] in {"VERIFIED", "INFERRED", "NOT_ESTABLISHED", "CONTRADICTED"}
            for reference in claim["evidence"]:
                assert_reference_resolves(reference)


def test_q1_oracle_references_resolve_at_the_frozen_commit() -> None:
    oracle_document = load(ORACLES)
    oracle = next(item for item in oracle_document["oracles"] if item["caseId"] == "Q1")
    for reference in oracle["requiredEvidenceReferences"]:
        assert_git_reference_resolves(reference, oracle_document["repositoryRevision"])
    for claim in oracle["requiredClaims"]:
        for reference in claim["evidence"]:
            assert_git_reference_resolves(reference, oracle_document["repositoryRevision"])


def test_q1_corrected_oracle_digest_and_scoring_are_coherent() -> None:
    oracle_document = load(ORACLES)
    oracle = next(item for item in oracle_document["oracles"] if item["caseId"] == "Q1")
    canonical = {key: value for key, value in oracle.items() if key != "canonicalDigest"}
    encoded = json.dumps(canonical, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    assert oracle["status"] == "ACCEPTED"
    assert oracle["canonicalDigest"] == hashlib.sha256(encoded).hexdigest()
    assert [claim["id"] for claim in oracle["requiredClaims"]] == [
        "Q1-C1", "Q1-C2", "Q1-C3", "Q1-C4"
    ]
    assert {claim["status"] for claim in oracle["requiredClaims"]} == {"VERIFIED"}
    assert sum(oracle["scoring"]["weights"].values()) == 100
    assert "PLAN/ACTION/FINAL is mandatory for every request." in oracle["forbiddenClaims"]


def test_first_test_is_provider_free_proposal_and_asymmetric_comparison() -> None:
    manifest = load(FIRST_TEST)
    assert manifest["status"] == "PROPOSED_DO_NOT_EXECUTE"
    assert manifest["caseId"] == "Q1"
    assert manifest["experimentType"] == "END_TO_END_SYSTEM_COMPARISON"
    assert manifest["toolEnvelope"]["parityMode"] == "END_TO_END_SYSTEM_COMPARISON"
    assert manifest["toolEnvelope"]["capabilityDifferencePolicy"] == (
        "record_actual_capabilities_do_not_equalize"
    )
    assert [entry["observationId"] for entry in manifest["observations"]] == [
        "Q1-A-DEVLOG-CURRENT-r1",
        "Q1-B-DIRECT-INVESTIGATOR-r1",
        "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1",
    ]
    assert manifest["evaluationNormalization"]["status"] == "READY_PROVIDER_FREE"
    assert manifest["answerContracts"]["A"] != manifest["answerContracts"]["B"]
    assert manifest["humanAuthorizationRequired"]
    assert NORMALIZATION_CONTRACT.is_file()
    assert ORACLE_REVIEW.is_file()


def test_treatment_inputs_do_not_embed_oracle_payload() -> None:
    benchmark_text = BENCHMARK.read_text(encoding="utf-8")
    first_test_text = FIRST_TEST.read_text(encoding="utf-8")
    runner_text = "".join(path.read_text(encoding="utf-8") for path in RUNNER.glob("*.py"))
    oracle = load(ORACLES)
    for entry in oracle["oracles"]:
        for claim in entry["requiredClaims"]:
            assert claim["statement"] not in benchmark_text
            assert claim["statement"] not in first_test_text
            assert claim["statement"] not in runner_text


def assert_reference_resolves(reference: str) -> None:
    path_text, _, locator = reference.partition("#")
    path = ROOT / path_text
    assert path.is_file(), reference
    if not locator:
        return
    match = re.fullmatch(r"L(\d+)(?:-L(\d+))?", locator)
    assert match, reference
    start = int(match.group(1))
    end = int(match.group(2) or start)
    line_count = len(path.read_text(encoding="utf-8").splitlines())
    assert 1 <= start <= end <= line_count, reference


def assert_git_reference_resolves(reference: str, revision: str) -> None:
    path_text, _, locator = reference.partition("#")
    result = subprocess.run(
        ["git", "show", f"{revision}:{path_text}"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == 0, reference
    if not locator:
        return
    match = re.fullmatch(r"L(\d+)(?:-L(\d+))?", locator)
    assert match, reference
    start = int(match.group(1))
    end = int(match.group(2) or start)
    line_count = len(result.stdout.splitlines())
    assert 1 <= start <= end <= line_count, reference
