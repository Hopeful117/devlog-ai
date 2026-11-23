"""Deterministic native-contract fixtures used only by the Q1 dry-run."""

from __future__ import annotations

from copy import deepcopy
from typing import Any

from app.schemas.story_context_analysis import ProviderStoryContextAnalysisResult


REVISION = "fa33f03d9af42e3ac6e64112ecebe38d86de7b9b"
_DIGEST = "0" * 64


def _story_result(*, treatment: str) -> dict[str, Any]:
    result = ProviderStoryContextAnalysisResult.model_validate(
        {
            "objectiveUnderstanding": {
                "summary": f"Deterministic {treatment} Q1 fixture.",
                "keyDomains": ["story-context"],
                "primaryTechnologies": ["Java", "Python"],
                "architecturalPatterns": ["Core authority boundary"],
            },
            "architectureFindings": [
                {
                    "title": "Core owns orchestration",
                    "description": "Core authorizes the request before AI execution.",
                    "grounding": {
                        "evidenceReferences": [
                            {"reference": "file:backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java#L119-L141"}
                        ],
                        "classification": "FACTUAL_EXTRACTION",
                        "grounded": True,
                    },
                }
            ],
            "confidence": "MEDIUM",
            "provenance": {
                "contextDigest": _DIGEST,
                "promptVersion": f"story-context-dry-run/{treatment.lower()}",
                "provider": "fixture",
                "modelIdentifier": "deterministic-fixture",
                "promptContentDigest": _DIGEST,
                "intentId": "q1-dry-run",
                "intentVersion": "1.0",
            },
            "outputClassification": {
                "entries": [
                    {
                        "findingReference": "architectureFindings[0]",
                        "classification": "FACTUAL_EXTRACTION",
                        "grounded": True,
                    }
                ]
            },
        }
    )
    return result.model_dump(mode="json", by_alias=True)


def story_context_fixture(*, treatment: str, investigation: bool = False) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "executionStatus": "COMPLETED",
        "semanticEligibility": "NOT_EVALUATED",
        "finalResponse": _story_result(treatment=treatment),
        "evidence": [
            {
                "reference": "file:backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java#L119-L141",
                "revision": REVISION,
                "sourceType": "TECHNICAL_EVIDENCE",
            }
        ],
    }
    if investigation:
        payload["investigationTrace"] = [
            {
                "actionId": "fixture-action-1",
                "kind": "SEARCH_EVIDENCE",
                "executionStatus": "COMPLETED",
                "resultIdentity": "fixture-result-1",
                "resultSha256": _DIGEST,
            },
            {
                "actionId": "fixture-action-2",
                "kind": "READ_EVIDENCE_RANGE",
                "executionStatus": "COMPLETED",
                "resultIdentity": "fixture-result-2",
                "resultSha256": _DIGEST,
            },
        ]
    return payload


def direct_investigator_fixture() -> dict[str, Any]:
    return {
        "executionStatus": "COMPLETED",
        "semanticEligibility": "NOT_EVALUATED",
        "finalResponse": {
            "answerText": "Core authorizes and validates the Story Context flow; Python generates and investigates within that authorization.",
            "claims": [
                {
                    "text": "Core authorizes the Story Context operation before AI execution.",
                    "claimType": "FACT",
                    "references": [
                        "file:backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java#L119-L141"
                    ],
                },
                {
                    "text": "Python does not own repository authorization.",
                    "claimType": "INTERPRETATION",
                    "references": [
                        "file:ai-engine/app/services/story_context_analysis_generation_service.py#L224-L285"
                    ],
                },
            ],
            "evidence": [
                {
                    "reference": "file:backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java#L119-L141",
                    "revision": REVISION,
                    "locator": {"kind": "LINE_RANGE", "startLine": 119, "endLine": 141},
                    "excerpt": "Core prepares and submits the Story Context task.",
                    "role": "DIRECT",
                },
                {
                    "reference": "file:ai-engine/app/services/story_context_analysis_generation_service.py#L224-L285",
                    "revision": REVISION,
                    "locator": {"kind": "LINE_RANGE", "startLine": 224, "endLine": 285},
                    "excerpt": "The AI Engine proposes bounded investigation actions.",
                    "role": "DIRECT",
                },
            ],
            "toolTrace": [
                {"operation": "search_repository", "executionStatus": "COMPLETED"},
                {"operation": "read_file", "executionStatus": "COMPLETED"},
            ],
            "abstention": False,
        },
    }


def fixture_for(condition: str) -> dict[str, Any]:
    if condition == "A_DEVLOG_CURRENT":
        return deepcopy(story_context_fixture(treatment="A"))
    if condition == "B_DIRECT_INVESTIGATOR":
        return deepcopy(direct_investigator_fixture())
    if condition == "C_DEVLOG_ENGINEERING_ANALYST":
        return deepcopy(story_context_fixture(treatment="C", investigation=True))
    raise ValueError(f"unsupported dry-run condition: {condition}")
