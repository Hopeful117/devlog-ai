"""Story 0245: short citable reference aliases.

A citable reference embeds a source identifier and a full commit hash (about
110 characters), and one mistyped character invalidates the whole answer. The
prompt therefore lists each citable reference under a short alias (``R1`` ..
``Rn``, in Core's order), and the model may cite the alias.

Before any validation, Python replaces every alias with its exact reference.
This is a deterministic lookup, not a fuzzy match:

* only an exact alias that is in the table is replaced;
* an unknown alias is left as is, so grounding validation rejects it;
* an exact reference is left as is.

Java Core still validates the exact references authoritatively.
"""

from __future__ import annotations

import re
from typing import Any

from pydantic import BaseModel

ALIAS_PATTERN = re.compile(r"^R[1-9][0-9]{0,3}$")


def citable_references(grounding_contract: dict[str, object] | None) -> list[str]:
    """Exact citable references, in Core's order, from the typed grounding allow-list."""
    if not isinstance(grounding_contract, dict):
        return []
    allowed = grounding_contract.get("allowedGroundingReferences")
    if not isinstance(allowed, list):
        return []
    references: list[str] = []
    for entry in allowed:
        if isinstance(entry, dict) and isinstance(entry.get("ref"), str) and entry["ref"] not in references:
            references.append(entry["ref"])
    return references


def reference_aliases(grounding_contract: dict[str, object] | None) -> dict[str, str]:
    """Alias table ``{"R1": exact reference, ...}`` derived only from the contract."""
    return {f"R{index}": reference
            for index, reference in enumerate(citable_references(grounding_contract), start=1)}


def alias_for(grounding_contract: dict[str, object] | None) -> dict[str, str]:
    """Reverse table ``{exact reference: alias}``."""
    return {reference: alias for alias, reference in reference_aliases(grounding_contract).items()}


def resolve_reference_aliases(output: Any, grounding_contract: dict[str, object] | None) -> int:
    """Replace, in place, every ``reference`` field holding a known alias.

    Returns the number of replacements. Walks nested models, lists and dicts.
    """
    table = reference_aliases(grounding_contract)
    if not table:
        return 0
    return _walk(output, table, set())


def _walk(value: Any, table: dict[str, str], seen: set[int]) -> int:
    if id(value) in seen:
        return 0
    if isinstance(value, BaseModel):
        seen.add(id(value))
        replaced = 0
        for name in type(value).model_fields:
            field_value = getattr(value, name, None)
            if name == "reference" and isinstance(field_value, str):
                candidate = field_value.strip()
                if ALIAS_PATTERN.match(candidate) and candidate in table:
                    setattr(value, name, table[candidate])
                    replaced += 1
                continue
            replaced += _walk(field_value, table, seen)
        return replaced
    if isinstance(value, (list, tuple)):
        return sum(_walk(item, table, seen) for item in value)
    if isinstance(value, dict):
        return sum(_walk(item, table, seen) for item in value.values())
    return 0
