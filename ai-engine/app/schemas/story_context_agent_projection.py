"""Versioned, read-only Story Context Agent projection contract (ADR-068/069)."""

from typing import Any, Literal
from decimal import Decimal
import hashlib
import json
import math
import re
import unicodedata
from pydantic import Field, model_validator, StrictInt
from app.schemas.ai_task import ContractModel

_PATH_KEYS = {"path", "file", "resource"}

def _path_key(key: str) -> bool:
    key = key.lower()
    return key in _PATH_KEYS or key == "files" or key.endswith("path") or key.endswith("file")

def _canonical_path(value: str) -> str:
    value = unicodedata.normalize("NFC", value).replace("\\", "/")
    if value.startswith("/") or re.match(r"^[A-Za-z]:/", value):
        raise ValueError("absolute paths are not canonical")
    parts = []
    for part in value.split("/"):
        if part in ("", "."):
            continue
        if part == "..":
            raise ValueError("path traversal is not canonical")
        parts.append(part)
    return "/".join(parts)

def _canonical(value, field=None):
    if isinstance(value, str):
        value = unicodedata.normalize("NFC", value)
        return _canonical_path(value) if field is not None and _path_key(str(field)) else value
    if isinstance(value, bool) or value is None:
        return value
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("non-finite number")
        return Decimal(str(value))
    if isinstance(value, dict):
        pairs = [(unicodedata.normalize("NFC", str(k)), k) for k in value]
        if len({normalized for normalized, _ in pairs}) != len(pairs):
            raise ValueError("canonical object contains colliding NFC keys")
        return {k: _canonical(value[original], k) for k, original in sorted(pairs)}
    if isinstance(value, list):
        return [_canonical(item, "path" if field is not None and _path_key(str(field)) else None) for item in value]
    return value

def _json(value):
    if isinstance(value, Decimal):
        if value == 0: return "0"
        return format(value.normalize(), "f")
    if isinstance(value, dict):
        return "{" + ",".join(json.dumps(k, ensure_ascii=False, separators=(",", ":")) + ":" + _json(v) for k, v in value.items()) + "}"
    if isinstance(value, list):
        return "[" + ",".join(_json(v) for v in value) + "]"
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), allow_nan=False)

def _projection_digest(value):
    payload = dict(value)
    payload.pop("projectionDigest", None)
    # The compatibility envelope mirrors the root identity.  Exclude that
    # mirror from the identity input to avoid a self-referential digest.
    compatibility = payload.get("compatibility")
    if isinstance(compatibility, dict):
        selected = compatibility.get("selectedKnowledge")
        if isinstance(selected, dict) and "projectionDigest" in selected:
            payload["compatibility"] = dict(compatibility)
            payload["compatibility"]["selectedKnowledge"] = dict(selected)
            payload["compatibility"]["selectedKnowledge"].pop("projectionDigest", None)
    return hashlib.sha256(_json(_canonical(payload)).encode("utf-8")).hexdigest()


from typing import Any, Literal
class StoryAgentReference(ContractModel):
    type: Literal["REPOSITORY_EVIDENCE"]
    ref: str = Field(min_length=1)
    scope: Literal["PROJECT_REVISION"]

class StoryAgentSourceRevision(ContractModel):
    kind: Literal["PROJECT_REVISION"]
    project: str = Field(min_length=1)
    revision: str = Field(min_length=1)

class StoryAgentFreshness(ContractModel):
    source_revision: StoryAgentSourceRevision = Field(alias="sourceRevision")
    state: Literal["FRESH", "STALE", "UNKNOWN", "NOT_ESTABLISHED"]

class StoryAgentAccounting(ContractModel):
    candidate_count: StrictInt = Field(alias="candidateCount", ge=0, le=2**31 - 1)
    selected_count: StrictInt = Field(alias="selectedCount", ge=0, le=2**31 - 1)
    discarded_count: StrictInt = Field(alias="discardedCount", ge=0, le=2**31 - 1)
    used_tokens: StrictInt = Field(alias="usedTokens", ge=0, le=2**31 - 1)
    budget: StrictInt = Field(ge=0, le=2**31 - 1)
    truncated: bool
    warnings: list[dict[str, Any]] = Field(default_factory=list)

    @model_validator(mode="after")
    def bounded(self):
        if self.used_tokens > self.budget:
            raise ValueError("usedTokens must not exceed budget")
        return self

class StoryAgentCompatibility(ContractModel):
    selection_digest: str = Field(alias="selectionDigest", min_length=64, max_length=64, pattern="^[0-9a-f]{64}$")
    selected_knowledge: dict[str, Any] = Field(alias="selectedKnowledge")

    @model_validator(mode="after")
    def validate_legacy_envelope(self):
        value = self.selected_knowledge.get("value")
        if self.selected_knowledge.get("contractVersion") != "selected-knowledge-compat/v1" or not isinstance(value, dict):
            raise ValueError("selectedKnowledge compatibility envelope is invalid")
        if not self.selected_knowledge.get("contextDigest") or not self.selected_knowledge.get("projectionDigest"):
            raise ValueError("selectedKnowledge compatibility identities are required")
        digest = hashlib.sha256(_json(_canonical(value)).encode("utf-8")).hexdigest()
        if digest != self.selection_digest:
            raise ValueError("selectionDigest does not identify selectedKnowledge.value")
        return self

class StoryContextAgentProjectionV1(ContractModel):
    @model_validator(mode="before")
    @classmethod
    def reject_explicit_null_compatibility(cls, value):
        if isinstance(value, dict) and "compatibility" in value and value["compatibility"] is None:
            raise ValueError("compatibility must be omitted when absent, not null")
        return value

    contract_version: Literal["story-context-agent-projection/v1"] = Field(alias="contractVersion")
    projection_version: Literal["sca/v1"] = Field(alias="projectionVersion")
    context_digest: str = Field(alias="contextDigest", min_length=64, max_length=64, pattern="^[0-9a-f]{64}$")
    projection_digest: str = Field(alias="projectionDigest", min_length=64, max_length=64, pattern="^[0-9a-f]{64}$")
    request: dict[str, Any]
    request_echo: dict[str, Any] = Field(alias="requestEcho")
    scope: dict[str, Any]
    freshness: StoryAgentFreshness
    context: dict[str, Any]
    grounding_candidates: dict[str, list[dict[str, Any]]] = Field(alias="groundingCandidates")
    accounting: StoryAgentAccounting
    policy: dict[str, Any]
    compatibility: StoryAgentCompatibility | None = None

    @model_validator(mode="after")
    def validate_contract(self):
        if _projection_digest(self.model_dump(by_alias=True, exclude_none=True)) != self.projection_digest:
            raise ValueError("projectionDigest does not identify the canonical projection")
        if self.context_digest == self.projection_digest:
            raise ValueError("contextDigest and projectionDigest must identify different layers")
        if self.compatibility is not None:
            if self.compatibility.selected_knowledge.get("contextDigest") != self.context_digest:
                raise ValueError("selectedKnowledge.contextDigest must equal root contextDigest")
            if self.compatibility.selected_knowledge.get("projectionDigest") != self.projection_digest:
                raise ValueError("selectedKnowledge.projectionDigest must equal root projectionDigest")
        required = {"projectSlug", "storyId", "intent", "files"}
        if set(self.request) != required or set(self.request_echo) != required:
            raise ValueError("request and requestEcho must contain exactly the canonical keys")
        if self.request != self.request_echo or self.scope != self.request:
            raise ValueError("requestEcho and scope must exactly equal request")
        if not isinstance(self.request["projectSlug"], str) or not self.request["projectSlug"]:
            raise ValueError("projectSlug is required")
        if not isinstance(self.request["intent"], str) or not self.request["intent"]:
            raise ValueError("intent is required")
        if not isinstance(self.request["files"], list) or any(not isinstance(item, str) for item in self.request["files"]):
            raise ValueError("files must be a list of strings")
        if self.freshness.source_revision.project != self.scope["projectSlug"]:
            raise ValueError("freshness source project must match scope")
        if "guidance" in self.context and self.context["guidance"] is None:
            raise ValueError("guidance must be omitted when not provided")
        if self.freshness.source_revision.revision in {"UNKNOWN", "UNSPECIFIED"} or not self.freshness.source_revision.revision.strip():
            raise ValueError("source revision must be resolvable")
        evidence = self.grounding_candidates.get("repositoryEvidence")
        if not isinstance(evidence, list):
            raise ValueError("repositoryEvidence grounding candidates are required")
        snapshot = self.context.get("repositoryEvidence")
        if not isinstance(snapshot, list):
            raise ValueError("repositoryEvidence snapshot is required")
        snapshot_by_ref = {item.get("reference"): item for item in snapshot if isinstance(item, dict)}
        revision = self.freshness.source_revision.revision
        for candidate in evidence:
            if (not isinstance(candidate, dict) or not isinstance(candidate.get("source"), dict)
                    or candidate.get("source", {}).get("provenance") is None or not candidate.get("trust")):
                raise ValueError("grounding provenance and trust are required")
            reference = StoryAgentReference.model_validate(candidate.get("reference"))
            item = snapshot_by_ref.get(reference.ref)
            if item is None:
                raise ValueError("grounding reference is not present in the evidence snapshot")
            source = candidate["source"]
            if source.get("provenance") != item.get("provenance"):
                raise ValueError("grounding provenance does not match the evidence snapshot")
            content = item.get("content")
            if not isinstance(content, dict) or content.get("revision") != revision:
                raise ValueError("grounding revision does not match the evidence snapshot")
            if source.get("project") not in (None, self.scope["projectSlug"]):
                raise ValueError("grounding project does not match scope")
            if source.get("revision") not in (None, revision):
                raise ValueError("grounding revision does not match scope")
        return self
