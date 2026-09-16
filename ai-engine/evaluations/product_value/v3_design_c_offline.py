"""Offline Design C rendering and size-fixture utilities."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from app.schemas.story_context_analysis import CausalEvidenceRole

from .causal_mapping import build_frozen_causal_questions
from .loader import apply_frozen_oracle, load_benchmark
from .repository_ground_truth import build_ground_truth_contexts
from .v2_live_runner import _selected_knowledge
from .v3_design_c import (
    CausalProviderAssessment,
    CausalProviderClassificationEntry,
    CausalProviderEvidenceAssertion,
    CausalProviderEvidenceRef,
    CausalProviderLocator,
    CausalProviderOutputClassification,
    CausalProviderProvenance,
    CausalProviderResult,
    build_causal_prompt,
    context_selection_identity,
    projection_items,
    projected_selected_knowledge,
    provider_schema_identity,
)


ROOT = Path(__file__).resolve().parents[3]
TRADING_OS = Path("/home/ludo/Bureau/workspace/trading-os")
BENCHMARK_PATH = ROOT / "docs/stories/0130-devlog-product-value-parity-investigation/evaluation/benchmark-suite-v1.json"
ORACLE_PATH = ROOT / "docs/stories/0131-devlog-product-value-evaluation-harness/evaluation/oracle-freeze-v1.json"
REVISION = "18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149"


def render_smoke_inputs() -> dict[str, Any]:
    benchmark = apply_frozen_oracle(
        load_benchmark(BENCHMARK_PATH), json.loads(ORACLE_PATH.read_text(encoding="utf-8")),
    )
    contexts = build_ground_truth_contexts(benchmark, TRADING_OS)
    questions = build_frozen_causal_questions(benchmark)
    candidates = {
        "CASE-01": "CASE-01::CL-01",
        "CASE-04": "CASE-04::CASE-04",
        "CASE-03": "CASE-03::CL-09",
    }
    context_identity = context_selection_identity()
    rendered = {}
    for case_id, question_id in candidates.items():
        full_context = next(item["context"] for item in contexts["cases"] if item["caseId"] == case_id)
        full_selected = _selected_knowledge(full_context, REVISION)
        items = projection_items(case_id, TRADING_OS, REVISION)
        projected = projected_selected_knowledge(full_selected, items, context_identity["digest"])
        question = next(q for q in questions[case_id] if q.question_id == question_id)
        question_payload = {
            "source": question.source, "target": question.target,
            "relationAsked": question.relation_asked, "answerRequired": question.answer_required,
        }
        prompt = build_causal_prompt(
            question=question_payload, visible_items=items,
            context_digest=context_identity["digest"],
        )
        user = prompt["userMessage"]
        evidence_bytes = sum(item["contentByteLength"] for item in items)
        rendered[case_id] = {
            "caseId": case_id, "questionId": question_id,
            "contextSelection": context_identity,
            "authorizedEvidenceIdentities": [item["reference"] for item in full_selected["repositoryContext"]["evidence"]],
            "providerVisibleEvidenceIdentities": [item["reference"] for item in items],
            "providerVisibleEvidenceChars": sum(len(item["content"]) for item in items),
            "providerVisibleEvidenceBytes": evidence_bytes,
            "providerVisibleEvidenceItems": len(items),
            "systemPromptChars": len(prompt["systemMessage"]),
            "userPromptChars": len(user),
            "inputPromptChars": len(prompt["systemMessage"]) + len(user),
            "providerSchema": provider_schema_identity(),
            "promptDigest": prompt["promptDigest"],
            "projectedSelectedKnowledge": projected,
            "prompt": prompt,
        }
    return rendered


def _provenance() -> CausalProviderProvenance:
    return CausalProviderProvenance(
        contextDigest="a" * 64, promptVersion="story0132-v3-design-c-prompt-v1",
        provider="openai", modelIdentifier="gpt-4.1-mini", promptContentDigest="b" * 64,
        intentId="engineering-story-context-causal-v2", intentVersion="v1",
    )


def _fixture(case_id: str, count: int, classification: str, explanation_length: int) -> CausalProviderResult:
    refs = [
        "docs/architecture/adr/ADR-042.md", "docs/architecture/adr/ADR-043.md",
        "docs/architecture/stories/0039-persisted-account-identity-explicit-paper-provisioning/story.md",
        "commit:6ea180ceab3c112807bc30818b1e7c19e38ae929",
    ][:count]
    assertions = [CausalProviderEvidenceAssertion(
        evidenceReference=CausalProviderEvidenceRef(
            reference=reference, role=CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT,
        ),
        locator=CausalProviderLocator(kind="SECTION", heading="4. Decision"),
        assertionRole=CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT,
    ) for reference in refs]
    return CausalProviderResult(
        causalAssessment=CausalProviderAssessment(
            question={"source": "ADR-042", "target": "Story-0039", "relationAsked": "CAUSAL", "answerRequired": True},
            classification=classification, evidenceAssertions=assertions,
            explanation="x" * explanation_length,
        ),
        confidence="MEDIUM", provenance=_provenance(),
        outputClassification=CausalProviderOutputClassification(entries=[
            CausalProviderClassificationEntry(
                findingReference="causalAssessment", classification="AI_INTERPRETATION", grounded=True,
            ),
        ]),
        causalClaims=[],
    )


def size_fixtures() -> dict[str, Any]:
    fixtures = {
        "minimumAffirmative": _fixture("CASE-01", 1, "EXPLICITLY_DOCUMENTED", 1),
        "maximumContractValidAffirmative": _fixture("CASE-01", 4, "STRONGLY_SUPPORTED", 5000),
        "validAbstention": _fixture("CASE-04", 0, "NOT_ESTABLISHED", 1),
        "evidenceHeavyValidPositive": _fixture("CASE-03", 4, "STRONGLY_SUPPORTED", 5000),
    }
    return {name: {
        "characters": len(result.model_dump_json(by_alias=True)),
        "utf8Bytes": len(result.model_dump_json(by_alias=True).encode("utf-8")),
        "json": result.model_dump(mode="json", by_alias=True),
    } for name, result in fixtures.items()}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = {"inputs": render_smoke_inputs(), "sizeFixtures": size_fixtures()}
    rendered = json.dumps(report, indent=2, ensure_ascii=False, sort_keys=True)
    if args.output:
        args.output.write_text(rendered + "\n", encoding="utf-8")
    else:
        print(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
