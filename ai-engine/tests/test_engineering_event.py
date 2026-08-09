from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.schemas.engineering_event import (
    EngineeringEventCategory,
    EngineeringEventGenerationOutput,
)


def proposal(category: EngineeringEventCategory) -> dict[str, object]:
    return {
        "schemaVersion": "engineering-event-proposal-v1",
        "category": category.value,
        "title": f"Validated {category.value}",
        "summary": "A bounded repository change was observed.",
        "significance": "The change matters to future engineering work.",
        "confidence": 0.8,
        "supportingFactIds": [str(uuid4())],
        "supportingObservationIds": [],
        "evidenceReferences": [],
    }


def test_accepts_every_v1_category() -> None:
    output = EngineeringEventGenerationOutput.model_validate(
        {"proposals": [proposal(category) for category in EngineeringEventCategory]}
    )
    assert [item.category for item in output.proposals] == list(EngineeringEventCategory)


def test_rejects_unsupported_or_ungrounded_events() -> None:
    invalid_category = proposal(EngineeringEventCategory.BUG_RESOLUTION)
    invalid_category["category"] = "DECISION"
    with pytest.raises(ValidationError):
        EngineeringEventGenerationOutput.model_validate({"proposals": [invalid_category]})

    ungrounded = proposal(EngineeringEventCategory.BUG_RESOLUTION)
    ungrounded["supportingFactIds"] = []
    with pytest.raises(ValidationError):
        EngineeringEventGenerationOutput.model_validate({"proposals": [ungrounded]})
