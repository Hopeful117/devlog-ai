"""Sequential Design C live smoke runner.

Importing this module performs no provider call. The runner is intentionally
evaluation-only and stops on the first infrastructure or structural failure.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import subprocess
import tempfile
from pathlib import Path
from uuid import uuid4

from openai import APIConnectionError, APIStatusError, APITimeoutError, AsyncOpenAI

from .v3_design_c import (
    CausalProviderResult,
    build_causal_prompt,
    causal_to_story_context,
    context_selection_identity,
    execution_configuration_identity,
    provider_schema,
    provider_schema_identity,
    projection_digest,
    validate_provider_schema,
    validate_causal_result,
)
from .v3_design_c_offline import render_smoke_inputs
from .v3_protocol import assert_safe_value, canonical, safe_metadata, sha256_text, write_immutable_artifact
from .v3_structured_output import ProviderParseFailure, capture_then_parse_openai_response


ROOT = Path(__file__).resolve().parents[3]
TRADING_OS = Path("/home/ludo/Bureau/workspace/trading-os")
MAPPING_HASH = "67474f09e41c07899c8c7117754b21e794f285380ffd50675e701a0a3c2c40c0"
EXECUTION_CONFIGURATION = {
    "provider": "openai", "model": "gpt-4.1-mini", "maxOutputTokens": 2500,
    "openaiSdkMaxRetries": 0, "providerAdapterMaxRetries": 0,
    "technicalRetryMax": 1, "maximumTotalAttemptsPerSlot": 2,
    "semanticRetry": "DISABLED", "timeoutSeconds": 90,
    "temperature": "PROVIDER_DEFAULT", "topP": "PROVIDER_DEFAULT",
    "seed": "NOT_CONFIGURED", "tools": "NOT_CONFIGURED",
    "reasoning": "NOT_CONFIGURED", "rawCaptureBoundary": "SDK_RESPONSE_BEFORE_PARSE",
}
SLOTS = (
    ("CASE-01", "CASE-01::CL-01"),
    ("CASE-04", "CASE-04::CASE-04"),
    ("CASE-03", "CASE-03::CL-09"),
)


def _environment_conflicts() -> None:
    expected = {
        "LLM_PROVIDER": "openai", "LLM_MODEL": "gpt-4.1-mini",
        "LLM_MAX_OUTPUT_TOKENS": "2500", "LLM_MAX_RETRIES": "0",
        "LLM_TIMEOUT_SECONDS": "90",
    }
    for name, value in expected.items():
        actual = os.environ.get(name)
        if actual is not None and actual != value:
            raise RuntimeError(f"Design C environment conflict for {name}: expected {value!r}, got {actual!r}")


def _status(error: Exception) -> str:
    if isinstance(error, APITimeoutError):
        return "TIMEOUT"
    if isinstance(error, APIConnectionError):
        return "NETWORK_FAILURE"
    if isinstance(error, APIStatusError):
        if error.status_code == 429:
            return "RATE_LIMITED"
        if error.status_code >= 500:
            return "PROVIDER_UNAVAILABLE"
    return "PROVIDER_EXECUTION_FAILURE"


def _provider_format() -> dict:
    return {
        "type": "json_schema", "strict": True, "name": "CausalProviderResult",
        "schema": provider_schema(),
    }


def _preflight(rendered: dict) -> None:
    manifest = json.loads((ROOT / "ai-engine/evaluations/product_value/v3/design-c-manifest.json").read_text(encoding="utf-8"))
    context = context_selection_identity()
    schema = provider_schema_identity()
    execution = execution_configuration_identity()
    if manifest["mappingHash"] != MAPPING_HASH:
        raise RuntimeError("Design C mapping hash mismatch")
    if manifest["contextSelection"]["revision"] != context["revision"] or manifest["contextSelection"]["digest"] != context["digest"]:
        raise RuntimeError("Design C context-selection manifest mismatch")
    if manifest["providerSchema"] != schema:
        raise RuntimeError("Design C provider-schema manifest mismatch")
    if manifest["executionConfiguration"]["revision"] != execution["revision"] or manifest["executionConfiguration"]["digest"] != execution["digest"]:
        raise RuntimeError("Design C execution manifest mismatch")
    if {key: value for key, value in EXECUTION_CONFIGURATION.items()} != {
        key: value for key, value in manifest["executionConfiguration"].items()
        if key not in {"revision", "digest"}
    }:
        raise RuntimeError("Design C provider configuration mismatch")
    validate_provider_schema(provider_schema())
    if set(rendered) != {"CASE-01", "CASE-04", "CASE-03"}:
        raise RuntimeError("Design C smoke case set mismatch")
    for case_id, case in rendered.items():
        if case["contextSelection"] != context or case["providerSchema"] != schema:
            raise RuntimeError(f"{case_id} identity mismatch")
        if projection_digest(case_id) != manifest["projectionDigestByCase"][case_id]:
            raise RuntimeError(f"{case_id} projection digest mismatch")
        if case["providerVisibleEvidenceIdentities"] != manifest["providerVisibleEvidence"][case_id]:
            raise RuntimeError(f"{case_id} provider-visible evidence mismatch")
        evidence = case["projectedSelectedKnowledge"]["repositoryContext"]["evidence"]
        if case["providerVisibleEvidenceBytes"] != sum(
            len(item["content"]["text"].encode("utf-8")) for item in evidence
        ):
            raise RuntimeError(f"{case_id} projection byte budget mismatch")
    if not os.environ.get("LLM_API_KEY"):
        raise RuntimeError("LLM_API_KEY is required for Design C smoke")
    assert_safe_value(EXECUTION_CONFIGURATION, field="providerConfiguration")


def _core_validate(result: dict, selected: dict, grounding: dict, context_digest: str) -> dict:
    with tempfile.TemporaryDirectory(prefix="design-c-core-") as directory:
        input_path = Path(directory) / "input.json"
        output_path = Path(directory) / "output.json"
        input_path.write_text(json.dumps({"items": [{
            "index": 0, "result": result, "selectedKnowledge": selected,
            "groundingContract": grounding, "contextDigest": context_digest,
        }]}, ensure_ascii=False), encoding="utf-8")
        subprocess.run([
            str(ROOT / "backend" / "mvnw"), "-o", "-q", "-pl", "backend", "-am", "test",
            "-Dtest=CoreV2EvaluationBridgeTest", "-Dsurefire.failIfNoSpecifiedTests=false",
            f"-Dstory0132.bridge.input={input_path}",
            f"-Dstory0132.bridge.output={output_path}",
        ], cwd=ROOT, check=True, timeout=180)
        return json.loads(output_path.read_text(encoding="utf-8"))["items"][0]


def _replay(slot: dict) -> dict:
    return _core_validate(
        slot["coreShapedResult"], slot["projectedSelectedKnowledge"],
        slot["groundingContract"], slot["contextDigest"],
    )


async def _execute_slot(
    *, client: AsyncOpenAI, rendered: dict, case_id: str, question_id: str,
    run_id: str, counters: dict[str, int],
) -> dict:
    prompt = rendered["prompt"]
    visible = set(rendered["providerVisibleEvidenceIdentities"])
    attempts = []
    capture = None
    provider_result = None
    error = None
    attempt = 1
    while True:
        counters["providerCalls"] += 1
        try:
            response = await client.responses.create(
                model="gpt-4.1-mini",
                input=[
                    {"role": "system", "content": prompt["systemMessage"]},
                    {"role": "user", "content": prompt["userMessage"]},
                ],
                text={"format": _provider_format()},
                max_output_tokens=2500,
            )
            capture, parsed = capture_then_parse_openai_response(response, CausalProviderResult)
            provider_result = parsed.output_parsed
            attempts.append({"attempt": attempt, "status": "SUCCEEDED", "responsePresent": True,
                             "providerMetadata": safe_metadata(capture.provider_metadata)})
            break
        except ProviderParseFailure as caught:
            capture = caught.capture
            error = caught
            attempts.append({"attempt": attempt, "status": "STRUCTURAL_FAILURE", "responsePresent": True,
                             "providerMetadata": safe_metadata(capture.provider_metadata), "error": str(caught)})
            break
        except Exception as caught:
            error = caught
            status = _status(caught)
            attempts.append({"attempt": attempt, "status": status, "responsePresent": False,
                             "error": str(caught)})
            if status not in {"TIMEOUT", "NETWORK_FAILURE", "PROVIDER_UNAVAILABLE", "RATE_LIMITED"} or attempt >= 2:
                break
            counters["technicalRetries"] += 1
            attempt += 1

    slot = {
        "runId": run_id, "caseId": case_id, "questionId": question_id,
        "repetition": 1, "mappingHash": MAPPING_HASH,
        "contextSelection": context_selection_identity(),
        "providerSchema": provider_schema_identity(),
        "executionConfiguration": execution_configuration_identity(),
        "providerConfiguration": EXECUTION_CONFIGURATION,
        "contextDigest": rendered["contextSelection"]["digest"],
        "projectedSelectedKnowledge": rendered["projectedSelectedKnowledge"],
        "authorizedEvidenceIdentities": rendered["authorizedEvidenceIdentities"],
        "providerVisibleEvidence": rendered["providerVisibleEvidenceIdentities"],
        "providerVisibleEvidenceBytes": rendered["providerVisibleEvidenceBytes"],
        "prompt": prompt, "attempts": attempts,
        "rawProviderResponse": capture.raw_provider_response if capture else {},
        "rawProviderResponseSha256": capture.raw_response_capture.get("rawResponseSha256") if capture else None,
        "providerMetadata": safe_metadata(capture.provider_metadata) if capture else {},
        "providerOutputText": capture.provider_text_output if capture else None,
        "providerResult": provider_result.model_dump(mode="json", by_alias=True) if provider_result else None,
        "gates": {}, "semanticEligible": False,
    }
    if provider_result is None:
        slot["gates"] = {"transport": "PASS" if capture else "FAIL", "structural": "FAIL"}
        slot["failure"] = str(error)
        return slot

    try:
        validate_causal_result(provider_result, visible)
        adapter_result = causal_to_story_context(
            provider_result, context_digest=slot["contextDigest"],
            prompt_digest=prompt["promptDigest"], provider="openai", model="gpt-4.1-mini",
        )
        core = _core_validate(adapter_result, rendered["projectedSelectedKnowledge"],
                              prompt["groundingContract"], slot["contextDigest"])
        slot["coreShapedResult"] = adapter_result
        slot["coreValidation"] = core
        slot["gates"] = {"transport": "PASS", "structural": "PASS", "contextIdentity": "PASS",
                          "referenceAuthorization": "PASS", "evidenceValidity": "PASS",
                          "coreValidation": "PASS" if core.get("ok") else "FAIL"}
        slot["semanticEligible"] = bool(core.get("ok"))
        if not core.get("ok"):
            slot["failure"] = core.get("error", "Core validation rejected result")
            return slot
        replay = _replay(slot)
        slot["offlineReplay"] = {"equal": replay.get("ok") is True and replay.get("result") == core.get("result"),
                                  "result": replay}
        if not slot["offlineReplay"]["equal"]:
            slot["gates"]["offlineReplay"] = "FAIL"
            slot["semanticEligible"] = False
            return slot
        slot["gates"]["offlineReplay"] = "PASS"
        slot["preliminaryClassification"] = provider_result.causal_assessment.classification.value
        return slot
    except Exception as caught:
        slot["gates"] = {"transport": "PASS", "structural": "PASS", "contextIdentity": "FAIL"}
        slot["failure"] = str(caught)
        return slot


async def run_design_c_smoke(*, output_directory: Path) -> dict:
    _environment_conflicts()
    if not os.environ.get("LLM_API_KEY"):
        raise RuntimeError("LLM_API_KEY is required for Design C smoke")
    if context_selection_identity()["digest"] != "de3d7236b83f752c68347c9a3bf03ec2f97c15f8869ba170c9b83ecdad1dbf90":
        raise RuntimeError("Design C context identity mismatch")
    if provider_schema_identity()["digest"] != "6b291e77e1708b0bc3a745869544f8ba9215389d469811e1205b9cd3e129dd24":
        raise RuntimeError("Design C provider schema identity mismatch")
    if execution_configuration_identity()["digest"] != "b47bb5205d7757b3998ebdb2aa88f85989fb7f23a5bd921767d8fe0c46aaa2cf":
        raise RuntimeError("Design C execution identity mismatch")
    rendered = render_smoke_inputs()
    _preflight(rendered)
    client = AsyncOpenAI(api_key=os.environ["LLM_API_KEY"], timeout=90, max_retries=0)
    run_id = str(uuid4())
    counters = {"providerCalls": 0, "technicalRetries": 0}
    slots = []
    for case_id, question_id in SLOTS:
        slot = await _execute_slot(client=client, rendered=rendered[case_id], case_id=case_id,
                                   question_id=question_id, run_id=run_id, counters=counters)
        slots.append(slot)
        if not slot.get("semanticEligible"):
            break
    artifact = {
        "artifactVersion": "story0132-v3-design-c-live-smoke-1.0.1",
        "runId": run_id, "mappingHash": MAPPING_HASH,
        "contextSelection": context_selection_identity(),
        "providerSchema": provider_schema_identity(),
        "executionConfiguration": execution_configuration_identity(),
        "providerConfiguration": EXECUTION_CONFIGURATION,
        "providerSchemaJson": provider_schema(), "slots": slots,
        "providerCallAccounting": {**counters, "expected": 3, "maximum": 6},
        "immutable": True,
    }
    artifact["artifactSha256"] = sha256_text(canonical(artifact))
    assert_safe_value(artifact, field="smokeArtifact")
    output_directory.mkdir(parents=True, exist_ok=True)
    path = output_directory / f"{run_id}.json"
    write_immutable_artifact(path, artifact)
    return {"artifactPath": str(path), "artifact": artifact}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-directory", type=Path, default=ROOT / "ai-engine/evaluations/product_value/v3/design-c-smoke")
    args = parser.parse_args()
    result = asyncio.run(run_design_c_smoke(output_directory=args.output_directory))
    print(json.dumps({"status": "DESIGN_C_SMOKE_COMPLETE", "artifactPath": result["artifactPath"],
                      "providerCalls": result["artifact"]["providerCallAccounting"]["providerCalls"]}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
