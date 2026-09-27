"""Named Story 0152 contract fixtures shared by Python and interop tests.

The values intentionally omit ``projectionDigest``; callers derive it after
applying a case mutation so the fixture cannot accidentally bless a stale
digest.  Keys mirror the names in the story/reviewer checklist.
"""

from copy import deepcopy


def _base() -> dict:
    return {
        "contractVersion": "story-context-agent-projection/v1",
        "projectionVersion": "sca/v1",
        "contextDigest": "a" * 64,
        "request": {"projectSlug": "devlog-ai", "storyId": None, "intent": "summarize", "files": []},
        "requestEcho": {"projectSlug": "devlog-ai", "storyId": None, "intent": "summarize", "files": []},
        "scope": {"projectSlug": "devlog-ai", "storyId": None, "intent": "summarize", "files": []},
        "freshness": {"sourceRevision": {"kind": "PROJECT_REVISION", "project": "devlog-ai", "revision": "rev-1"}, "state": "FRESH"},
        "context": {"project": {}, "sections": [], "repositoryEvidence": [], "relations": []},
        "groundingCandidates": {"repositoryEvidence": []},
        "accounting": {"candidateCount": 0, "selectedCount": 0, "discardedCount": 0, "usedTokens": 0, "budget": 0, "truncated": False, "warnings": []},
        "policy": {"compositionVersion": "ctx/v1", "projectionVersion": "sca/v1"},
    }


def fixture(name: str) -> dict:
    """Return an independent named fixture payload (without its digest)."""
    value = deepcopy(_FIXTURES[name])
    return value


_FIXTURES = {
    "empty-collections": _base(),
    "empty-guidance": {**_base(), "context": {**_base()["context"], "guidance": {}}},
    "guidance-absent": _base(),
    "foreign-project": {**_base(), "freshness": {"sourceRevision": {"kind": "PROJECT_REVISION", "project": "other", "revision": "rev-1"}, "state": "FRESH"}},
    "unicode-nfc": {**_base(), "context": {**_base()["context"], "sections": ["café"]}},
    # Explicit null is intentionally distinct from the omitted optional envelope.
    "null-vs-omitted": {**_base(), "compatibility": None},
    "story-id-null": _base(),
    # v1-only payload: compatibility/legacy optional scalars are absent, never null.
    "optional-scalars-omitted": {k: v for k, v in _base().items() if k != "compatibility"},
    "ordered-lists": {**_base(), "context": {**_base()["context"], "sections": ["a", "b"]}},
    "budget-truncated": {**_base(), "accounting": {"candidateCount": 2, "selectedCount": 1, "discardedCount": 1, "usedTokens": 8, "budget": 8, "truncated": True, "warnings": [{"code": "BUDGET", "message": "reduced"}]}},
    "project-revision-valid-invalid": _base(),
    "adr068-reference-valid-invalid": {**_base(), "groundingCandidates": {"repositoryEvidence": [{"reference": {"type": "REPOSITORY_EVIDENCE", "ref": "README.md", "scope": "PROJECT_REVISION"}, "source": {"provenance": "CORE"}, "trust": "HIGH"}]}},
    "legacy-selected-knowledge": {
        **_base(),
        "compatibility": {
            "selectionDigest": "0" * 64,
            "selectedKnowledge": {
                "contractVersion": "selected-knowledge-compat/v1",
                "contextDigest": "a" * 64,
                "projectionDigest": "0" * 64,
                "value": {"repositoryContext": {"evidence": []}},
            },
        },
    },
}


FIXTURE_NAMES = tuple(_FIXTURES)
