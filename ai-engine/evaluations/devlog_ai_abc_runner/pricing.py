"""Explicit pricing contract for a future Q1 campaign.

No provider rates are bundled here. A campaign must supply an approved,
versioned pricing table and bind its digest into the authorization document.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
import hashlib
import json
from typing import Any, Mapping


class PricingContractError(ValueError):
    """Raised when pricing data is absent or cannot support accounting."""


NON_METERED_BILLING_MODEL = "CHATGPT_OAUTH_SUBSCRIPTION_NOT_METERED"


def canonical_pricing_digest(contract: Mapping[str, Any]) -> str:
    """Digest the complete pricing artifact, excluding its self-reported digest."""

    canonical = {key: value for key, value in contract.items() if key != "digest"}
    return hashlib.sha256(
        json.dumps(canonical, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()


def validate_pricing_contract(contract: Mapping[str, Any]) -> None:
    if contract.get("status") != "APPROVED":
        raise PricingContractError("pricing contract is not approved")
    if any(not isinstance(contract.get(key), str) or not contract[key].strip()
           for key in ("version", "currency")):
        raise PricingContractError("pricing version and currency are required")
    if contract.get("digest") != canonical_pricing_digest(contract):
        raise PricingContractError("pricing digest does not match the artifact")
    if contract.get("billingModel") == NON_METERED_BILLING_MODEL:
        if contract.get("currency") != "NOT_APPLICABLE":
            raise PricingContractError("non-metered pricing must use NOT_APPLICABLE currency")
        if contract.get("costCeilingPolicy") != "OPERATIONAL_LIMITS_ONLY":
            raise PricingContractError("non-metered pricing requires operational limits only")
        if contract.get("inputPerMillion") is not None or contract.get("outputPerMillion") is not None:
            raise PricingContractError("non-metered pricing must not declare token rates")
        return
    for key in ("inputPerMillion", "outputPerMillion"):
        try:
            rate = Decimal(str(contract[key]))
        except (KeyError, ValueError, TypeError) as error:
            raise PricingContractError(f"pricing rate is invalid: {key}") from error
        if not rate.is_finite() or rate < 0:
            raise PricingContractError(f"pricing rate is invalid: {key}")


@dataclass(frozen=True)
class PricingTable:
    version: str
    digest: str
    currency: str
    input_per_million: Decimal
    output_per_million: Decimal
    cached_input_per_million: Decimal | None = None
    reasoning_output_per_million: Decimal | None = None

    def __post_init__(self) -> None:
        if not self.version.strip() or not self.currency.strip():
            raise PricingContractError("pricing version and currency are required")
        if len(self.digest) != 64 or any(character not in "0123456789abcdef" for character in self.digest):
            raise PricingContractError("pricing digest must be a SHA-256 hex digest")
        rates = (
            self.input_per_million,
            self.output_per_million,
            self.cached_input_per_million,
            self.reasoning_output_per_million,
        )
        if any(rate is not None and rate < 0 for rate in rates):
            raise PricingContractError("pricing rates must not be negative")

    def calculate(
        self,
        *,
        input_tokens: int,
        output_tokens: int,
        cached_input_tokens: int = 0,
        reasoning_output_tokens: int = 0,
    ) -> Decimal:
        counts = (input_tokens, output_tokens, cached_input_tokens, reasoning_output_tokens)
        if any(isinstance(value, bool) or not isinstance(value, int) or value < 0 for value in counts):
            raise PricingContractError("token usage must be non-negative integers")
        if cached_input_tokens > input_tokens:
            raise PricingContractError("cached input tokens exceed input tokens")
        if cached_input_tokens and self.cached_input_per_million is None:
            raise PricingContractError("cached input pricing is unavailable")
        if reasoning_output_tokens and self.reasoning_output_per_million is None:
            raise PricingContractError("reasoning output pricing is unavailable")
        input_rate = (
            self.cached_input_per_million
            if self.cached_input_per_million is not None
            else self.input_per_million
        )
        standard_input_tokens = input_tokens - cached_input_tokens
        return (
            Decimal(standard_input_tokens) * self.input_per_million
            + Decimal(cached_input_tokens) * input_rate
            + Decimal(output_tokens) * self.output_per_million
            + Decimal(reasoning_output_tokens) * (self.reasoning_output_per_million or Decimal(0))
        ) / Decimal(1_000_000)
