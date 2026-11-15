"""Evaluation-only A/B/C output normalization.

This package never changes production contracts and never performs semantic
oracle evaluation. It converts immutable raw observations into a deterministic
derived representation for later human/oracle scoring.
"""

from .normalizer import (
    NORMALIZATION_CONTRACT_VERSION,
    normalize_direct_investigator,
    normalize_story_context,
    render_evaluation_display,
)

__all__ = [
    "NORMALIZATION_CONTRACT_VERSION",
    "normalize_direct_investigator",
    "normalize_story_context",
    "render_evaluation_display",
]
