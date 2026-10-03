import hashlib

import pytest
from pydantic import ValidationError

from app.schemas.story_context_agent_projection import (
    StoryAgentReference, StoryContextAgentProjectionV1, _canonical, _json, _projection_digest
)
from tests.story_context_agent_projection_fixtures import FIXTURE_NAMES, fixture

CONTEXT_DIGEST = "a" * 64

def projection(**overrides):
    value = {
        "protocolVersion": "story-context-agent-protocol/v1",
        "contractVersion": "story-context-agent-projection/v1", "projectionVersion": "sca/v1",
        "contextDigest": CONTEXT_DIGEST,
        "request": {"projectSlug": "devlog-ai", "storyId": None, "intent": "summarize", "files": []},
        "requestEcho": {"projectSlug": "devlog-ai", "storyId": None, "intent": "summarize", "files": []},
        "scope": {"projectSlug": "devlog-ai", "storyId": None, "intent": "summarize", "files": []},
        "freshness": {"sourceRevision": {"kind": "PROJECT_REVISION", "project": "devlog-ai", "revision": "rev-1"}, "state": "FRESH"},
        "context": {"project": {}, "sections": [], "repositoryEvidence": [], "relations": []},
        "groundingCandidates": {"repositoryEvidence": []},
        "accounting": {"candidateCount": 0, "selectedCount": 0, "discardedCount": 0, "usedTokens": 0, "budget": 0, "truncated": False, "warnings": []},
        "policy": {"compositionVersion": "ctx/v1", "projectionVersion": "sca/v1"},
    }
    value.update(overrides)
    value["projectionDigest"] = _projection_digest(value)
    return value



def test_nfc_interop_vector_and_key_collision():
    value = {"z": "é", "a": ["é", 1.0, 0]}
    canonical = _json(_canonical(value))
    assert canonical == '{"a":["é",1,0],"z":"é"}'
    assert hashlib.sha256(canonical.encode()).hexdigest() == "50584fd784854a417c365b8f4fbdde11b15c506cef1519222f5d5eb943472b7d"
    with pytest.raises(ValueError, match="colliding NFC"):
        _canonical({"é": 1, "é": 2})


def test_reference_extra_forbid_and_repository_scope_rejected():
    with pytest.raises(ValidationError):
        StoryAgentReference.model_validate({"type": "REPOSITORY_EVIDENCE", "ref": "r", "scope": "PROJECT_REVISION", "extra": 1})
    with pytest.raises(ValidationError):
        StoryAgentReference.model_validate({"type": "REPOSITORY_EVIDENCE", "ref": "r", "scope": "REPOSITORY"})


def test_empty_collections_and_null_are_distinct():
    assert _json(_canonical({"empty": [], "object": {}, "value": None})) == '{"empty":[],"object":{},"value":null}'


def test_path_budget_inputs_are_canonicalizable():
    assert _canonical({"files": ["docs/./story.md"]}) == {"files": ["docs/story.md"]}
    with pytest.raises(ValueError):
        _canonical({"path": "../secret"})


def test_empty_guidance_and_guidance_absent_are_distinct_but_valid():
    empty = projection(context={"project": {}, "sections": [], "repositoryEvidence": [], "relations": [], "guidance": {}})
    absent = projection()
    assert StoryContextAgentProjectionV1.model_validate(empty).context["guidance"] == {}
    assert "guidance" not in StoryContextAgentProjectionV1.model_validate(absent).context
    assert empty["projectionDigest"] != absent["projectionDigest"]


def test_optional_guidance_is_never_null():
    with pytest.raises(ValidationError, match="guidance"):
        StoryContextAgentProjectionV1.model_validate(
            projection(context={"project": {}, "sections": [], "repositoryEvidence": [], "relations": [], "guidance": None})
        )


def test_named_null_and_omitted_fixtures_are_discriminating():
    null_value = fixture("null-vs-omitted")
    omitted_value = fixture("optional-scalars-omitted")
    assert "compatibility" in null_value and null_value["compatibility"] is None
    assert "compatibility" not in omitted_value
    assert _projection_digest(null_value) != _projection_digest(omitted_value)
    with pytest.raises(ValidationError, match="compatibility"):
        StoryContextAgentProjectionV1.model_validate(projection(**null_value))
    StoryContextAgentProjectionV1.model_validate(projection(**omitted_value))


def test_legacy_fixture_contains_real_compatibility_envelope():
    value = fixture("legacy-selected-knowledge")
    compatibility = value["compatibility"]
    selected = compatibility["selectedKnowledge"]
    compatibility["selectionDigest"] = hashlib.sha256(
        _json(_canonical(selected["value"])).encode()
    ).hexdigest()
    payload = projection(**value)
    selected["projectionDigest"] = payload["projectionDigest"]
    payload["compatibility"] = compatibility
    payload["projectionDigest"] = _projection_digest({k: v for k, v in payload.items() if k != "projectionDigest"})
    selected["projectionDigest"] = payload["projectionDigest"]
    parsed = StoryContextAgentProjectionV1.model_validate(payload)
    assert parsed.compatibility is not None
    assert parsed.compatibility.selected_knowledge["contractVersion"] == "selected-knowledge-compat/v1"


def test_story_id_null_and_optional_scalars_omitted_are_wire_valid():
    parsed = StoryContextAgentProjectionV1.model_validate(projection())
    assert parsed.request["storyId"] is None
    assert "selectionDigest" not in parsed.model_dump(by_alias=True)
    assert "selectedKnowledge" not in parsed.model_dump(by_alias=True)


def test_question_aware_projection_v2_requires_and_preserves_question():
    payload = projection()
    for key in ("request", "requestEcho", "scope"):
        payload[key] = {**payload[key], "question": "Which component owns polling?"}
    payload["contractVersion"] = "story-context-agent-projection/v2"
    payload["projectionVersion"] = "sca/v2"
    payload["policy"] = {"compositionVersion": "ctx/v1", "projectionVersion": "sca/v2"}
    payload["projectionDigest"] = _projection_digest(payload)

    parsed = StoryContextAgentProjectionV1.model_validate(payload)

    assert parsed.request["question"] == "Which component owns polling?"


def test_question_aware_projection_v2_rejects_missing_or_oversized_question():
    missing = projection()
    missing["contractVersion"] = "story-context-agent-projection/v2"
    missing["projectionVersion"] = "sca/v2"
    missing["policy"] = {"compositionVersion": "ctx/v1", "projectionVersion": "sca/v2"}
    missing["projectionDigest"] = _projection_digest(missing)
    with pytest.raises(ValidationError, match="canonical keys"):
        StoryContextAgentProjectionV1.model_validate(missing)

    oversized = projection()
    for key in ("request", "requestEcho", "scope"):
        oversized[key] = {**oversized[key], "question": "x" * 2001}
    oversized["contractVersion"] = "story-context-agent-projection/v2"
    oversized["projectionVersion"] = "sca/v2"
    oversized["policy"] = {"compositionVersion": "ctx/v1", "projectionVersion": "sca/v2"}
    oversized["projectionDigest"] = _projection_digest(oversized)
    with pytest.raises(ValidationError, match="question"):
        StoryContextAgentProjectionV1.model_validate(oversized)


def test_projection_rejects_mismatched_contract_and_projection_versions():
    payload = projection(
        contractVersion="story-context-agent-projection/v2",
        projectionVersion="sca/v1",
    )
    with pytest.raises(ValidationError, match="same version"):
        StoryContextAgentProjectionV1.model_validate(payload)


def test_foreign_project_and_invalid_revisions_fail_closed():
    with pytest.raises(ValidationError, match="source project"):
        StoryContextAgentProjectionV1.model_validate(
            projection(freshness={"sourceRevision": {"kind": "PROJECT_REVISION", "project": "other", "revision": "rev-1"}, "state": "FRESH"})
        )
    with pytest.raises(ValidationError, match="source revision"):
        StoryContextAgentProjectionV1.model_validate(
            projection(freshness={"sourceRevision": {"kind": "PROJECT_REVISION", "project": "devlog-ai", "revision": "UNKNOWN"}, "state": "UNKNOWN"})
        )


def test_order_and_budget_are_digest_inputs_and_accounting_is_bounded():
    first = projection(
        context={"project": {}, "sections": ["a", "b"], "repositoryEvidence": [], "relations": []},
        accounting={"candidateCount": 2, "selectedCount": 1, "discardedCount": 1, "usedTokens": 8, "budget": 8, "truncated": True, "warnings": [{"code": "BUDGET", "message": "reduced"}]},
    )
    second = projection(context={"project": {}, "sections": ["b", "a"], "repositoryEvidence": [], "relations": []})
    assert first["projectionDigest"] != second["projectionDigest"]
    with pytest.raises(ValidationError, match="usedTokens"):
        StoryContextAgentProjectionV1.model_validate(
            projection(accounting={"candidateCount": 1, "selectedCount": 1, "discardedCount": 0, "usedTokens": 9, "budget": 8, "truncated": True, "warnings": []})
        )


@pytest.mark.parametrize("field", ["candidateCount", "selectedCount", "discardedCount", "usedTokens", "budget"])
def test_accounting_requires_strict_bounded_integer(field):
    for invalid in (None, 1.5, -1, 2**31):
        accounting = {"candidateCount": 0, "selectedCount": 0, "discardedCount": 0,
                      "usedTokens": 0, "budget": 0, "truncated": False, "warnings": []}
        accounting[field] = invalid
        with pytest.raises(ValidationError):
            StoryContextAgentProjectionV1.model_validate(projection(accounting=accounting))


def test_adr068_reference_valid_and_invalid_scopes():
    candidate = {"reference": {"type": "REPOSITORY_EVIDENCE", "ref": "README.md", "scope": "PROJECT_REVISION"}, "source": {"provenance": "CORE"}, "trust": "HIGH"}
    snapshot = {"reference": "README.md", "provenance": "CORE", "content": {"revision": "rev-1"}}
    assert StoryContextAgentProjectionV1.model_validate(projection(
        context={"project": {}, "sections": [], "repositoryEvidence": [snapshot], "relations": []},
        groundingCandidates={"repositoryEvidence": [candidate]}))
    for scope in ("PROJECT", "STORY", "TASK"):
        bad = {**candidate, "reference": {**candidate["reference"], "scope": scope}}
        with pytest.raises(ValidationError):
            StoryContextAgentProjectionV1.model_validate(projection(groundingCandidates={"repositoryEvidence": [bad]}))

    with pytest.raises(ValidationError, match="snapshot"):
        StoryContextAgentProjectionV1.model_validate(projection(groundingCandidates={"repositoryEvidence": [candidate]}))


def test_non_empty_repository_evidence_wire_binds_reference_provenance_and_revision():
    candidate = {
        "reference": {"type": "REPOSITORY_EVIDENCE", "ref": "README.md", "scope": "PROJECT_REVISION"},
        "source": {"provenance": {"sourceType": "CORE", "identifier": "readme"}, "revision": "rev-1"},
        "trust": "TECHNICAL_EVIDENCE",
    }
    snapshot = {
        "reference": "README.md",
        "provenance": {"sourceType": "CORE", "identifier": "readme"},
        "content": {"status": "COMPLETE", "text": "readme", "revision": "rev-1"},
    }
    parsed = StoryContextAgentProjectionV1.model_validate(projection(
        context={"project": {}, "sections": [], "repositoryEvidence": [snapshot], "relations": []},
        groundingCandidates={"repositoryEvidence": [candidate]},
    ))
    assert parsed.grounding_candidates["repositoryEvidence"][0]["reference"]["ref"] == "README.md"


def test_legacy_selected_knowledge_compatibility_is_verified():
    value = {"repositoryContext": {"evidence": []}}
    selection_digest = hashlib.sha256(_json(_canonical(value)).encode()).hexdigest()
    payload = projection()
    payload["compatibility"] = {"selectionDigest": selection_digest, "selectedKnowledge": {"contractVersion": "selected-knowledge-compat/v1", "contextDigest": CONTEXT_DIGEST, "projectionDigest": payload["projectionDigest"], "value": value}}
    payload["projectionDigest"] = _projection_digest({k: v for k, v in payload.items() if k != "projectionDigest"})
    payload["compatibility"]["selectedKnowledge"]["projectionDigest"] = payload["projectionDigest"]
    assert StoryContextAgentProjectionV1.model_validate(payload).compatibility is not None
    payload["compatibility"]["selectionDigest"] = "b" * 64
    with pytest.raises(ValidationError, match="selectionDigest"):
        StoryContextAgentProjectionV1.model_validate(payload)


def test_named_story_0152_fixtures_are_present_and_validate():
    assert set(FIXTURE_NAMES) == {
        "empty-collections", "empty-guidance", "guidance-absent", "foreign-project",
        "unicode-nfc", "null-vs-omitted", "story-id-null", "optional-scalars-omitted",
        "ordered-lists", "budget-truncated", "project-revision-valid-invalid",
        "adr068-reference-valid-invalid", "legacy-selected-knowledge",
    }
    for name in FIXTURE_NAMES:
        payload = fixture(name)
        if name == "null-vs-omitted":
            with pytest.raises(ValidationError, match="compatibility"):
                StoryContextAgentProjectionV1.model_validate(projection(**payload))
            continue
        if name == "foreign-project":
            with pytest.raises(ValidationError):
                StoryContextAgentProjectionV1.model_validate(projection(**payload))
            continue
        if name == "project-revision-valid-invalid":
            payload["freshness"]["sourceRevision"]["revision"] = "UNKNOWN"
            with pytest.raises(ValidationError):
                StoryContextAgentProjectionV1.model_validate(projection(**payload))
            continue
        if name == "adr068-reference-valid-invalid":
            payload["groundingCandidates"]["repositoryEvidence"][0]["reference"]["scope"] = "PROJECT"
            with pytest.raises(ValidationError):
                StoryContextAgentProjectionV1.model_validate(projection(**payload))
            continue
        if name == "legacy-selected-knowledge":
            # The compatibility envelope's projectionDigest is coupled to the
            # root digest; its dedicated test above builds that identity.
            continue
        StoryContextAgentProjectionV1.model_validate(projection(**payload))
