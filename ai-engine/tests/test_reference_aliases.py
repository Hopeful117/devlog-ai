"""Story 0245: deterministic citable reference aliases."""

from app.prompts.reference_aliases import reference_aliases, resolve_reference_aliases
from app.schemas.story_context_analysis import ProviderStoryContextAnalysisResult
from tests.test_story_context_analysis_wire_contract import _provider_payload

CONTRACT = {"allowedGroundingReferences": [
    {"ref": "file:s:backend/A.java@r"}, {"ref": "document:s:docs/ADR-006.md@r"}]}


def _result_with(references):
    payload = _provider_payload("LOW")
    payload["objectiveUnderstanding"]["grounding"]["evidenceReferences"] = [
        {"reference": value} for value in references]
    return ProviderStoryContextAnalysisResult.model_validate(payload)


def test_alias_table_follows_core_order():
    assert reference_aliases(CONTRACT) == {
        "R1": "file:s:backend/A.java@r", "R2": "document:s:docs/ADR-006.md@r"}
    assert reference_aliases(None) == {}


def test_known_alias_resolves_and_everything_else_is_untouched():
    result = _result_with(["R2", "R9", "file:s:backend/A.java@r", "r1", "R1x"])

    replaced = resolve_reference_aliases(result, CONTRACT)

    refs = [value.reference for value in result.objective_understanding.grounding.evidence_references]
    assert replaced == 1
    assert refs == ["document:s:docs/ADR-006.md@r", "R9", "file:s:backend/A.java@r", "r1", "R1x"]


def test_no_contract_means_no_resolution():
    result = _result_with(["R1"])

    assert resolve_reference_aliases(result, {}) == 0
    assert result.objective_understanding.grounding.evidence_references[0].reference == "R1"
