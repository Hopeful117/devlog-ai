"""Provider-free tests for the Story 0223 8 x 3 x 3 campaign (Story 0237 AC-6)."""

from __future__ import annotations

import copy
import json
import re
from pathlib import Path

import pytest

from evaluations.devlog_ai_abc_runner import DRY_RUN, LIVE, ManifestValidationError, replay_run, run
from evaluations.devlog_ai_abc_runner.campaign import (
    CampaignError,
    assignment_matrix,
    batch_observation_ids,
    campaign_report,
    derive_observations,
    load_case_oracle,
    observation_id,
    validate_campaign_structure,
)
from evaluations.devlog_ai_abc_runner.live import (
    LiveAuthorizationError,
    canonical_manifest_digest,
    validate_live_authorization,
)
from evaluations.devlog_ai_abc_runner.runner import load_manifest


ROOT = Path(__file__).parents[1].parent
CAMPAIGN = ROOT / "docs/evaluation/devlog-ai-abc-campaign-manifest-v1.json"
ORACLES = ROOT / "docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json"
REGISTRY = ROOT / ("backend/src/main/java/com/hopeful117/devlogai/evaluation/livepreparation/"
                   "LiveEvaluationContractRegistry.java")
REVISION = "0673d1d32433cdaff054d5f1defd1bedd01c0039"
CASES = [f"Q{index}" for index in range(1, 9)]


def campaign() -> dict:
    return load_manifest(CAMPAIGN)


def test_campaign_manifest_is_the_deterministic_72_cell_matrix() -> None:
    manifest = campaign()

    assert manifest["caseIds"] == CASES
    assert manifest["repetitions"] == 3
    assert len(manifest["observations"]) == 72
    assert manifest["observations"][0]["observationId"] == "Q1-A-DEVLOG-CURRENT-r1"
    assert manifest["observations"][-1]["observationId"] == "Q8-C-DEVLOG-ENGINEERING-ANALYST-r3"
    assert len({item["observationId"] for item in manifest["observations"]}) == 72


def test_observation_ids_follow_the_core_derivation() -> None:
    assert observation_id("Q2", "B_DIRECT_INVESTIGATOR", 3) == "Q2-B-DIRECT-INVESTIGATOR-r3"
    assert batch_observation_ids(campaign(), "Q2") == [
        f"Q2-{condition}-r{repetition}"
        for repetition in (1, 2, 3)
        for condition in ("A-DEVLOG-CURRENT", "B-DIRECT-INVESTIGATOR", "C-DEVLOG-ENGINEERING-ANALYST")
    ]


def test_core_registry_binds_the_frozen_manifest_and_oracle_digests() -> None:
    source = REGISTRY.read_text(encoding="utf-8")
    block = source[source.index("ABC_CAMPAIGN = new LiveEvaluationContract("):]
    block = block[:block.index(";")]
    oracles = {item["caseId"]: item["canonicalDigest"]
               for item in json.loads(ORACLES.read_text(encoding="utf-8"))["oracles"]}

    assert canonical_manifest_digest(campaign()) in block
    registered = dict(re.findall(r'"(Q\d)", "([0-9a-f]{64})"', block))
    assert registered == oracles


@pytest.mark.parametrize("case_id", CASES)
def test_every_case_oracle_digest_is_frozen_and_verified(case_id: str) -> None:
    oracle = load_case_oracle(ORACLES, case_id, expected_revision=REVISION)

    assert oracle["status"] == "ACCEPTED"


def test_tampered_oracle_is_rejected(tmp_path: Path) -> None:
    document = json.loads(ORACLES.read_text(encoding="utf-8"))
    document["oracles"][3]["question"] += " (edited)"
    path = tmp_path / "oracles.json"
    path.write_text(json.dumps(document), encoding="utf-8")

    with pytest.raises(CampaignError, match="digest mismatch"):
        load_case_oracle(path, document["oracles"][3]["caseId"], expected_revision=REVISION)


@pytest.mark.parametrize("mutation", ["reorder", "repetitions", "single_case", "drop_case_version"])
def test_campaign_structure_rejects_non_deterministic_matrices(mutation: str) -> None:
    manifest = copy.deepcopy(campaign())
    if mutation == "reorder":
        manifest["observations"][0], manifest["observations"][1] = manifest["observations"][1], manifest["observations"][0]
    elif mutation == "repetitions":
        manifest["repetitions"] = 2
    elif mutation == "single_case":
        manifest["caseId"] = "Q1"
    else:
        del manifest["caseVersions"]["Q4"]

    with pytest.raises(CampaignError):
        validate_campaign_structure(manifest)


def test_unknown_case_batch_is_rejected() -> None:
    with pytest.raises(CampaignError):
        batch_observation_ids(campaign(), "Q9")


def test_assignment_matrix_keeps_missing_and_invalid_explicit() -> None:
    manifest = campaign()
    statuses = {
        "Q1-A-DEVLOG-CURRENT-r1": {"technicalStatus": "COMPLETED", "semanticEligibility": "NOT_EVALUATED"},
        "Q1-B-DIRECT-INVESTIGATOR-r1": {"technicalStatus": "RUNTIME_FAILURE"},
        "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1": {"technicalStatus": "COMPLETED",
                                               "semanticEligibility": "SEMANTICALLY_ELIGIBLE"},
    }

    matrix = assignment_matrix(manifest, statuses)

    assert matrix["counts"] == {"ASSIGNED": 72, "EXECUTED": 2, "SEMANTIC_ELIGIBLE": 1, "INVALID": 1, "MISSING": 69}
    assert matrix["cells"][1]["states"] == ["ASSIGNED", "INVALID"]


def test_case_batch_dry_run_replays_and_aggregates(tmp_path: Path) -> None:
    first = run(mode=DRY_RUN, manifest_path=CAMPAIGN, output_root=tmp_path / "q1",
                repository_root=ROOT, case_id="Q1")
    second = run(mode=DRY_RUN, manifest_path=CAMPAIGN, output_root=tmp_path / "q7",
                 repository_root=ROOT, case_id="Q7")

    assert first.summary["status"] == "CAMPAIGN_RUNNER_DRY_RUN_READY"
    assert first.summary["caseId"] == "Q1"
    assert first.summary["providerCalls"] == 0 and first.summary["networkCalls"] == 0
    assert first.summary["assignmentMatrix"]["counts"]["EXECUTED"] == 9
    assert replay_run(manifest_path=CAMPAIGN, run_root=first.run_root)["status"] == "PASS"
    raw = json.loads((first.run_root / "observations" / "Q1-B-DIRECT-INVESTIGATOR-r2" / "raw" / "answer.json")
                     .read_text(encoding="utf-8"))
    assert raw["caseId"] == "Q1"

    report = campaign_report(campaign(), [first.run_root, second.run_root])
    assert report["matrix"]["counts"]["EXECUTED"] == 18
    assert report["matrix"]["counts"]["MISSING"] == 54


def test_a_cell_recorded_twice_fails_closed(tmp_path: Path) -> None:
    first = run(mode=DRY_RUN, manifest_path=CAMPAIGN, output_root=tmp_path / "a",
                repository_root=ROOT, case_id="Q3")
    again = run(mode=DRY_RUN, manifest_path=CAMPAIGN, output_root=tmp_path / "b",
                repository_root=ROOT, case_id="Q3")

    with pytest.raises(CampaignError, match="more than one run"):
        campaign_report(campaign(), [first.run_root, again.run_root])


def test_observation_selection_must_stay_inside_the_batch(tmp_path: Path) -> None:
    with pytest.raises(ManifestValidationError, match="outside the case batch"):
        run(mode=DRY_RUN, manifest_path=CAMPAIGN, output_root=tmp_path / "x", repository_root=ROOT,
            case_id="Q2", observation_ids=("Q3-A-DEVLOG-CURRENT-r1",))


def test_campaign_live_step_requires_a_case_batch(tmp_path: Path) -> None:
    with pytest.raises(ManifestValidationError, match="--case-id"):
        run(mode=LIVE, manifest_path=CAMPAIGN, output_root=tmp_path / "live", repository_root=ROOT,
            oracle_path=ORACLES, authorization={"authorizationStatus": "AUTHORIZED"})


def _authorization(manifest: dict, case_id: str, oracle: dict, oracle_status: str) -> dict:
    pricing = json.loads((ROOT / "docs/evaluation/devlog-ai-q1-chatgpt-oauth-pricing-draft-v1.json")
                         .read_text(encoding="utf-8"))
    return {
        "artifactVersion": "bounded-live-evaluation-authorization-v1",
        "issuedBy": "CORE",
        "authorizationId": "auth-1",
        "planId": "plan-1",
        "planDigest": "a" * 64,
        "authorizationStatus": "AUTHORIZED",
        "approverId": "approver",
        "approvedAt": "2026-11-25T10:00:00Z",
        "campaignId": manifest["manifestId"],
        "manifestId": manifest["manifestId"],
        "manifestVersion": manifest["version"],
        "manifestDigest": canonical_manifest_digest(manifest),
        "oracleStatus": oracle_status,
        "oracleDigest": oracle["canonicalDigest"],
        "authorizedObservationIds": batch_observation_ids(manifest, case_id),
        "authorizedConditions": manifest["conditions"],
        "repositoryRevision": manifest["repositoryRevision"],
        "runtimeRevision": "1" * 40,
        "provider": manifest["provider"],
        "model": manifest["model"],
        "budgets": manifest["technicalBudgets"],
        "limits": manifest["technicalBudgets"],
        "pricingStatus": "APPROVED",
        "pricingVersion": pricing.get("version"),
        "pricingDigest": pricing.get("digest"),
        "costCeiling": {"currency": "NOT_APPLICABLE", "maxAmount": "NOT_MEASURED"},
        "expiresAtEpochSeconds": 4_102_444_800,
    }, pricing


def test_batch_authorization_names_the_case_oracle() -> None:
    manifest = campaign()
    oracle = load_case_oracle(ORACLES, "Q7", expected_revision=REVISION)
    authorization, pricing = _authorization(manifest, "Q7", oracle, "Q1_ORACLE_ACCEPTED")

    with pytest.raises(LiveAuthorizationError, match="Q7 oracle"):
        validate_live_authorization(authorization, manifest=manifest, oracle=oracle,
                                    pricing_contract=pricing, case_id="Q7")


def test_batch_authorization_must_cover_exactly_the_case_batch() -> None:
    manifest = campaign()
    oracle = load_case_oracle(ORACLES, "Q7", expected_revision=REVISION)
    authorization, pricing = _authorization(manifest, "Q7", oracle, "Q7_ORACLE_ACCEPTED")
    authorization["authorizedObservationIds"] = batch_observation_ids(manifest, "Q6")

    with pytest.raises(LiveAuthorizationError, match="observation set"):
        validate_live_authorization(authorization, manifest=manifest, oracle=oracle,
                                    pricing_contract=pricing, case_id="Q7")


def test_b_runtime_input_injects_the_case_question() -> None:
    from evaluations.comparative_baseline_v4 import runtime as v4_runtime
    from evaluations.devlog_ai_abc_runner.benchmark import load_case
    from evaluations.devlog_ai_abc_runner.q1_b_binding import _case_runtime_input

    case = load_case(ROOT, "Q7")
    with _case_runtime_input(case.case_id, case.version, case.question, REVISION):
        value = v4_runtime.condition_input("AGENT_DIRECT_OPEN", "Q7", 2)

    assert value["questionId"] == "Q7"
    assert value["question"] == case.question
    assert value["repetition"] == 2
    assert value["repositoryRevision"] == REVISION


def test_derive_observations_matches_manifest() -> None:
    manifest = campaign()

    assert derive_observations(manifest["caseIds"], manifest["conditions"], 3) == manifest["observations"]
