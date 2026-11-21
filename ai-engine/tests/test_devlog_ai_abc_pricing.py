"""Provider-free tests for the explicit Q1 pricing contract."""

from decimal import Decimal

import pytest

from evaluations.devlog_ai_abc_runner.pricing import PricingContractError, PricingTable, canonical_pricing_digest, validate_pricing_contract


def test_pricing_calculates_only_from_explicit_versioned_rates() -> None:
    table = PricingTable(
        version="test-pricing-v1",
        digest="a" * 64,
        currency="USD",
        input_per_million=Decimal("2"),
        output_per_million=Decimal("4"),
        cached_input_per_million=Decimal("0.5"),
        reasoning_output_per_million=Decimal("6"),
    )

    assert table.calculate(
        input_tokens=1_000_000,
        output_tokens=500_000,
        cached_input_tokens=200_000,
        reasoning_output_tokens=100_000,
    ) == Decimal("4.3")


@pytest.mark.parametrize(
    "kwargs",
    [
        {"digest": "not-a-digest"},
        {"input_per_million": Decimal("-1")},
    ],
)
def test_invalid_pricing_fails_closed(kwargs: dict[str, object]) -> None:
    values: dict[str, object] = {
        "version": "test-pricing-v1",
        "digest": "a" * 64,
        "currency": "USD",
        "input_per_million": Decimal("2"),
        "output_per_million": Decimal("4"),
    }
    values.update(kwargs)

    with pytest.raises(PricingContractError):
        PricingTable(**values)  # type: ignore[arg-type]


def test_missing_optional_rate_fails_closed_when_usage_requires_it() -> None:
    table = PricingTable(
        version="test-pricing-v1",
        digest="a" * 64,
        currency="USD",
        input_per_million=Decimal("2"),
        output_per_million=Decimal("4"),
    )

    with pytest.raises(PricingContractError, match="cached input pricing"):
        table.calculate(input_tokens=10, output_tokens=10, cached_input_tokens=1)


def test_non_metered_oauth_contract_uses_operational_limits_without_token_rates() -> None:
    contract = {
        "status": "APPROVED",
        "version": "q1-chatgpt-oauth-pricing-v1",
        "currency": "NOT_APPLICABLE",
        "billingModel": "CHATGPT_OAUTH_SUBSCRIPTION_NOT_METERED",
        "inputPerMillion": None,
        "outputPerMillion": None,
        "costCeilingPolicy": "OPERATIONAL_LIMITS_ONLY",
    }
    contract["digest"] = canonical_pricing_digest(contract)

    validate_pricing_contract(contract)
