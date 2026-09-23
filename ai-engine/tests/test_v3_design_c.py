import copy
from pathlib import Path

import pytest
from pydantic import ValidationError

from evaluations.product_value.v3_design_c import (
    CausalProviderResult,
    context_selection_identity,
    causal_to_story_context,
    provider_schema,
    projection_items,
    provider_schema_identity,
    validate_provider_schema,
    validate_causal_result,
)
from evaluations.product_value.v3_design_c_offline import render_smoke_inputs, size_fixtures
from evaluations.repository_paths import TRADING_OS_REPOSITORY


ROOT = Path(__file__).resolve().parents[2]
TRADING_OS = TRADING_OS_REPOSITORY
REVISION = "18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149"


def test_design_c_identities_are_frozen_and_distinct():
    context = context_selection_identity()
    schema = provider_schema_identity()

    assert context["revision"] != schema["revision"]
    assert len(context["digest"]) == 64
    assert len(schema["digest"]) == 64
    assert provider_schema()["properties"]["confidence"]["enum"] == ["HIGH", "MEDIUM", "LOW"]


def test_projection_manifest_has_exact_fidelity_for_all_smoke_slots():
    assert [(case, len(projection_items(case, TRADING_OS, REVISION))) for case in ("CASE-01", "CASE-04", "CASE-03")] == [
        ("CASE-01", 4), ("CASE-04", 4), ("CASE-03", 3),
    ]
    for case in ("CASE-01", "CASE-04", "CASE-03"):
        items = projection_items(case, TRADING_OS, REVISION)
        assert [item["ordering"] for item in items] == list(range(len(items)))
        assert all(item["contentByteLength"] == len(item["content"].encode("utf-8")) for item in items)
        assert all(len(item["contentSha256"]) == 64 for item in items)


def test_commit_projection_contains_only_manifest_selected_paths():
    item = projection_items("CASE-01", TRADING_OS, REVISION)[-1]
    content = item["content"]
    assert "trading-core/src/main/java/com/hope/trading/trading_core/model/Account.java" in content
    assert "trading-core/src/main/java/com/hope/trading/trading_core/brokeraccount/application/BrokerAccountService.java" in content
    assert "PositionController.java" not in content


def test_projection_digest_mismatch_fails_closed():
    item = copy.deepcopy(__import__(
        "evaluations.product_value.v3_design_c", fromlist=["_load_manifest"]
    )._load_manifest()["cases"]["CASE-01"][0])
    item["expectedSha256"] = "0" * 64
    with pytest.raises(ValueError, match="projection digest mismatch"):
        __import__(
            "evaluations.product_value.v3_design_c", fromlist=["resolve_projection"]
        ).resolve_projection(TRADING_OS, REVISION, item)


def test_provider_dto_has_only_causal_contract_and_strict_cardinalities():
    schema = provider_schema()
    assert set(schema["properties"]) == {
        "causalAssessment", "confidence", "provenance", "outputClassification", "causalClaims",
    }
    assert schema["properties"]["causalClaims"]["maxItems"] == 0
    assert schema["properties"]["causalClaims"]["items"]["type"] == "object"
    assert schema["properties"]["causalClaims"]["items"]["additionalProperties"] is False
    assert schema["$defs"]["CausalProviderAssessment"]["properties"]["evidenceAssertions"]["maxItems"] == 4


def test_provider_schema_preflight_recursively_accepts_corrected_schema():
    validate_provider_schema(provider_schema())


def test_provider_schema_preflight_rejects_untyped_array_items():
    schema = copy.deepcopy(provider_schema())
    schema["properties"]["causalClaims"]["items"] = {}
    with pytest.raises(ValueError, match="array items"):
        validate_provider_schema(schema)


def test_invisible_reference_is_rejected():
    fixture = size_fixtures()["minimumAffirmative"]["json"]
    fixture["causalAssessment"]["evidenceAssertions"][0]["evidenceReference"]["reference"] = "invisible.md"
    result = CausalProviderResult.model_validate(fixture)
    with pytest.raises(ValueError, match="not provider-visible"):
        validate_causal_result(result, {"visible.md"})


def test_zero_assertions_are_valid_for_abstention():
    result = CausalProviderResult.model_validate(size_fixtures()["validAbstention"]["json"])
    validate_causal_result(result, set())
    assert result.causal_assessment.classification.value == "NOT_ESTABLISHED"


def test_affirmative_requires_an_assertion():
    fixture = size_fixtures()["validAbstention"]["json"]
    fixture["causalAssessment"]["classification"] = "EXPLICITLY_DOCUMENTED"
    result = CausalProviderResult.model_validate(fixture)
    with pytest.raises(ValueError, match="requires an evidence assertion"):
        validate_causal_result(result, set())


def test_causal_claims_are_exactly_zero_and_confidence_is_string():
    fixture = size_fixtures()["validAbstention"]["json"]
    fixture["causalClaims"] = [{"unexpected": True}]
    with pytest.raises(ValidationError):
        CausalProviderResult.model_validate(fixture)
    assert provider_schema()["properties"]["confidence"]["type"] == "string"


def test_representative_invalid_payloads_fail_deterministically():
    base = size_fixtures()["minimumAffirmative"]["json"]
    invalid_confidence = copy.deepcopy(base)
    invalid_confidence["confidence"] = {"level": "HIGH"}
    with pytest.raises(ValidationError):
        CausalProviderResult.model_validate(invalid_confidence)

    missing_assessment = copy.deepcopy(base)
    del missing_assessment["causalAssessment"]
    with pytest.raises(ValidationError):
        CausalProviderResult.model_validate(missing_assessment)

    five_assertions = copy.deepcopy(base)
    five_assertions["causalAssessment"]["evidenceAssertions"] *= 5
    with pytest.raises(ValidationError):
        CausalProviderResult.model_validate(five_assertions)


def test_evaluation_adapter_mechanically_builds_core_shaped_result():
    result = CausalProviderResult.model_validate(size_fixtures()["minimumAffirmative"]["json"])
    adapted = causal_to_story_context(
        result, context_digest="c" * 64, prompt_digest="d" * 64,
        provider="fixture", model="fixture",
    )

    from app.schemas.story_context_analysis import StoryContextAnalysisResult

    bound = StoryContextAnalysisResult.model_validate(adapted)
    assert bound.causal_claims == []
    assert bound.causal_assessment is not None
    assert bound.provenance.context_digest == "c" * 64
    assert bound.confidence.level == "MEDIUM"


def test_offline_inputs_are_bounded_and_labels_are_not_provider_visible():
    rendered = render_smoke_inputs()
    assert set(rendered) == {"CASE-01", "CASE-03", "CASE-04"}
    assert rendered["CASE-01"]["providerVisibleEvidenceItems"] == 4
    assert rendered["CASE-01"]["providerVisibleEvidenceBytes"] == 32401
    for item in rendered.values():
        assert "DIRECT_REQUIRED" not in item["prompt"]["userMessage"]
        assert "SUPPORTING_REQUIRED" not in item["prompt"]["userMessage"]


def test_offline_size_fixtures_cover_required_shapes():
    fixtures = size_fixtures()
    assert fixtures["validAbstention"]["utf8Bytes"] == 745
    assert fixtures["minimumAffirmative"]["utf8Bytes"] == 1024
    assert fixtures["maximumContractValidAffirmative"]["utf8Bytes"] == 6919
    assert fixtures["evidenceHeavyValidPositive"]["utf8Bytes"] == 6919
