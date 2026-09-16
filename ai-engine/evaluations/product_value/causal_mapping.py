"""Frozen Story0132 benchmark-to-V2 causal-question projection."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any


CASE04_ID = "CASE-04"
CASE04_QUESTION_ID = "CASE-04::CASE-04"
CASE04_SOURCE = "ADR-043"
CASE04_TARGET = "ExecutionConfiguration refactor delivered by Story 0042"
CAUSAL_RELATION = "CAUSAL"


@dataclass(frozen=True)
class FrozenCausalQuestion:
    case_id: str
    question_id: str
    claim_id: str
    source: str
    target: str
    relation_asked: str
    answer_required: bool
    expected_classification: str
    expected_evidence: tuple[str, ...]
    mapping_authority: str


def build_frozen_causal_questions(
    benchmark: dict[str, Any],
) -> dict[str, tuple[FrozenCausalQuestion, ...]]:
    """Project frozen causal links into stable V2 question identities.

    CASE04 is the sole explicit human-freeze mapping. Its expected outcome is
    still read from the frozen evaluator-side case data and is never included
    in model-facing question/context payloads.
    """

    questions: dict[str, tuple[FrozenCausalQuestion, ...]] = {}
    for case in benchmark["cases"]:
        case_id = str(case["caseId"])
        expected_evidence = tuple(str(value) for value in case["expectedEvidence"])
        links = case.get("expectedCausalLinks", [])
        if links:
            questions[case_id] = tuple(
                FrozenCausalQuestion(
                    case_id=case_id,
                    question_id=f"{case_id}::{link['id']}",
                    claim_id=str(link["id"]),
                    source=str(link["from"]),
                    target=str(link["to"]),
                    relation_asked=CAUSAL_RELATION,
                    answer_required=True,
                    expected_classification=str(link["strength"]),
                    expected_evidence=expected_evidence,
                    mapping_authority="DETERMINISTIC_FROM_FROZEN_BENCHMARK",
                )
                for link in links
            )
            continue

        if case_id != CASE04_ID or not case.get("negativeCase"):
            raise ValueError(f"causal question mapping is missing for {case_id}")
        questions[case_id] = (
            FrozenCausalQuestion(
                case_id=CASE04_ID,
                question_id=CASE04_QUESTION_ID,
                claim_id=CASE04_ID,
                source=CASE04_SOURCE,
                target=CASE04_TARGET,
                relation_asked=CAUSAL_RELATION,
                answer_required=True,
                expected_classification=str(case["expectedOutcome"]),
                expected_evidence=expected_evidence,
                mapping_authority="EXPLICIT_HUMAN_FREEZE",
            ),
        )
    return questions


def expected_assessment_slots(
    benchmark: dict[str, Any], repetitions: int,
) -> dict[str, int]:
    """Return case-level assessment slots without hardcoded case counts."""

    if repetitions < 1:
        raise ValueError("repetitions must be positive")
    return {
        case_id: len(questions) * repetitions
        for case_id, questions in build_frozen_causal_questions(benchmark).items()
    }


def positive_question_ids(benchmark: dict[str, Any]) -> set[str]:
    """Return positive question identities, excluding the CASE04 control."""

    return {
        question.question_id
        for case_id, questions in build_frozen_causal_questions(benchmark).items()
        if case_id != CASE04_ID
        for question in questions
    }


def validate_assessment_slots(
    benchmark: dict[str, Any], records: list[dict[str, Any]], repetitions: int,
) -> list[str]:
    """Fail closed when case/question/repetition assessment identity is wrong."""

    questions = build_frozen_causal_questions(benchmark)
    expected = {
        (case_id, question.question_id, repetition)
        for case_id, items in questions.items()
        for question in items
        for repetition in range(1, repetitions + 1)
    }
    seen: set[tuple[str, str, int]] = set()
    errors: list[str] = []
    for record in records:
        try:
            key = (str(record["caseId"]), str(record["questionId"]), int(record["repetition"]))
        except (KeyError, TypeError, ValueError):
            errors.append("assessment record has invalid case/question/repetition identity")
            continue
        if key not in expected:
            errors.append(f"unknown assessment slot: {key}")
        elif key in seen:
            errors.append(f"duplicate assessment slot: {key}")
        else:
            seen.add(key)
    errors.extend(f"missing assessment slot: {key}" for key in sorted(expected - seen))
    return errors
