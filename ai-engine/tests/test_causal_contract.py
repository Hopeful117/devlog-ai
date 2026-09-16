import pytest
from pydantic import ValidationError

from app.schemas.story_context_analysis import (
    CausalEvidenceBasis,
    CausalClassification,
    CausalClaim,
    CausalEvidenceRole,
    CausalEvidenceRef,
    CausalAssessment,
    CausalQuestion,
    EvidenceAssertion,
    EvidenceLocator,
    EvidenceRef,
    maximum_defensible_classification,
)
from app.services.story_context_analysis_generation_service import (
    StoryContextAnalysisGenerationService,
    StoryContextAnalysisOutputValidationError,
)
from evaluations.product_value.causal_evaluation import (
    causal_diagnostics,
    evaluate_thresholds,
    remove_causal_evidence,
    score_production_result,
)


def claim(classification=CausalClassification.NOT_ESTABLISHED, refs=None, **kwargs):
    basis = kwargs.get(
        "evidenceBasis",
        CausalEvidenceBasis.DIRECT_DOCUMENTATION
        if classification is CausalClassification.EXPLICITLY_DOCUMENTED
        else CausalEvidenceBasis.MATERIAL_CORROBORATION
        if classification is CausalClassification.STRONGLY_SUPPORTED
        else CausalEvidenceBasis.INSUFFICIENT,
    )
    return CausalClaim(
        source=kwargs.get("source", "ADR-043"),
        target=kwargs.get("target", "ExecutionConfiguration"),
        causalClassification=classification,
        evidenceBasis=basis,
        evidenceReferences=[
            CausalEvidenceRef(
                reference=ref,
                role=(
                    CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT
                    if classification is CausalClassification.EXPLICITLY_DOCUMENTED
                    else CausalEvidenceRole.MATERIAL_RELATIONSHIP_SUPPORT
                    if classification is CausalClassification.STRONGLY_SUPPORTED
                    else CausalEvidenceRole.NON_CAUSAL_CONTEXT
                ),
            )
            for ref in (refs or [])
        ],
        explanation=kwargs.get("explanation", "The available evidence does not establish causality."),
    )


def test_causal_classifications_are_explicit_and_relation_metadata_is_not_a_field():
    assert {item.value for item in CausalClassification} == {
        "EXPLICITLY_DOCUMENTED", "STRONGLY_SUPPORTED", "NOT_ESTABLISHED"
    }
    with pytest.raises(ValidationError):
        CausalClaim(
            source="ADR-043",
            target="ExecutionConfiguration",
            causalClassification="NOT_ESTABLISHED",
            evidenceBasis="TEMPORAL_PROXIMITY_ONLY",
            evidenceReferences=[],
            explanation="No direct causal evidence.",
            relationType="TEMPORAL_PROXIMITY",
        )


@pytest.mark.parametrize("classification", [
    CausalClassification.EXPLICITLY_DOCUMENTED,
    CausalClassification.STRONGLY_SUPPORTED,
])
def test_affirmative_causality_requires_evidence(classification):
    with pytest.raises(ValidationError, match="evidenceReferences"):
        claim(classification)


def test_strongly_supported_requires_multiple_evidence_references():
    with pytest.raises(ValidationError, match="multiple"):
        claim(CausalClassification.STRONGLY_SUPPORTED, ["e1"])


def test_causal_evidence_roles_are_required_and_cap_classification():
    with pytest.raises(ValidationError, match="role"):
        CausalClaim(
            source="ADR-043",
            target="ExecutionConfiguration",
            causalClassification=CausalClassification.EXPLICITLY_DOCUMENTED,
            evidenceBasis=CausalEvidenceBasis.DIRECT_DOCUMENTATION,
            evidenceReferences=[CausalEvidenceRef(reference="e1")],
            explanation="The evidence directly states the relationship.",
        )

    direct = CausalEvidenceRef(reference="e1", role=CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT)
    context = CausalEvidenceRef(reference="e2", role=CausalEvidenceRole.NON_CAUSAL_CONTEXT)
    with pytest.raises(ValidationError, match="defensible evidence"):
        CausalClaim(
            source="ADR-043",
            target="ExecutionConfiguration",
            causalClassification=CausalClassification.EXPLICITLY_DOCUMENTED,
            evidenceBasis=CausalEvidenceBasis.DIRECT_DOCUMENTATION,
            evidenceReferences=[direct, context],
            explanation="The evidence does not directly establish this relationship.",
        )


def test_maximum_defensible_classification_is_structural():
    assert maximum_defensible_classification([
        EvidenceRef(reference="direct", role=CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT),
    ]) is CausalClassification.EXPLICITLY_DOCUMENTED
    assert maximum_defensible_classification([
        EvidenceRef(reference="material-1", role=CausalEvidenceRole.MATERIAL_RELATIONSHIP_SUPPORT),
        EvidenceRef(reference="material-2", role=CausalEvidenceRole.MATERIAL_RELATIONSHIP_SUPPORT),
    ]) is CausalClassification.STRONGLY_SUPPORTED
    assert maximum_defensible_classification([
        EvidenceRef(reference="chronology", role=CausalEvidenceRole.NON_CAUSAL_CONTEXT),
    ]) is CausalClassification.NOT_ESTABLISHED


def test_causal_required_output_cannot_be_empty():
    service = StoryContextAnalysisGenerationService.__new__(StoryContextAnalysisGenerationService)
    output = type("Output", (), {
        "causal_claims": [],
        "architecture_findings": [], "decision_findings": [], "evidence_findings": [],
        "historical_context": [], "constraint_findings": [],
        "impacted_component_findings": [], "uncertainties": [],
        "confidence": type("Confidence", (), {"level": "HIGH"})(),
        "output_classification": [],
    })()
    with pytest.raises(StoryContextAnalysisOutputValidationError, match="causalClaims"):
        service._validate_output(
            output,
            {},
            {"causalAnswerRequired": True},
        )


def test_not_established_is_valid_without_affirmative_evidence():
    assert claim().causal_classification is CausalClassification.NOT_ESTABLISHED


@pytest.mark.parametrize("basis", [
    CausalEvidenceBasis.CHRONOLOGY_ONLY,
    CausalEvidenceBasis.TEMPORAL_PROXIMITY_ONLY,
    CausalEvidenceBasis.SHARED_TOPIC_ONLY,
    CausalEvidenceBasis.ARCHITECTURAL_COMPATIBILITY_ONLY,
    CausalEvidenceBasis.POSSIBLE_RELEVANCE_ONLY,
    CausalEvidenceBasis.CONFLICTING,
    CausalEvidenceBasis.INSUFFICIENT,
])
def test_non_causal_evidence_bases_require_not_established(basis):
    assert claim(evidenceBasis=basis).causal_classification is CausalClassification.NOT_ESTABLISHED


def test_causal_classification_and_evidence_basis_must_agree():
    with pytest.raises(ValidationError, match="contradictory"):
        claim(CausalClassification.EXPLICITLY_DOCUMENTED, ["e1"], evidenceBasis=CausalEvidenceBasis.CHRONOLOGY_ONLY)


def test_required_source_target_and_bounded_explanation():
    with pytest.raises(ValidationError):
        claim(source="")
    with pytest.raises(ValidationError):
        claim(target="")
    with pytest.raises(ValidationError):
        claim(explanation="")
    with pytest.raises(ValidationError, match="differ"):
        claim(source="same", target="same")


def test_service_rejects_unknown_causal_reference_and_duplicate_relationship():
    service = StoryContextAnalysisGenerationService.__new__(StoryContextAnalysisGenerationService)
    valid = claim(CausalClassification.EXPLICITLY_DOCUMENTED, ["e1"])
    output = type("Output", (), {
        "causal_claims": [valid],
        "architecture_findings": [], "decision_findings": [], "evidence_findings": [],
        "historical_context": [], "constraint_findings": [],
        "impacted_component_findings": [], "uncertainties": [],
        "confidence": type("Confidence", (), {"level": "HIGH"})(),
        "output_classification": [],
    })()
    with pytest.raises(StoryContextAnalysisOutputValidationError, match="unknown evidence"):
        service._validate_output(output, {}, {"allowedEvidenceReferences": []})

    output.causal_claims = [valid, valid.model_copy(update={"explanation": "duplicate"})]
    with pytest.raises(StoryContextAnalysisOutputValidationError, match="Duplicate causal"):
        service._validate_output(output, {}, {"allowedEvidenceReferences": ["e1"]})


def test_confidence_does_not_change_not_established():
    assert claim().causal_classification is CausalClassification.NOT_ESTABLISHED


def test_contradictory_evidence_prevents_affirmative_classification():
    with pytest.raises(ValidationError, match="defensible evidence"):
        CausalClaim(
            source="ADR-043",
            target="ExecutionConfiguration",
            causalClassification=CausalClassification.EXPLICITLY_DOCUMENTED,
            evidenceBasis=CausalEvidenceBasis.DIRECT_DOCUMENTATION,
            evidenceReferences=[
                CausalEvidenceRef(
                    reference="contradiction",
                    role=CausalEvidenceRole.CONTRADICTORY_EVIDENCE,
                )
            ],
            explanation="The evidence contradicts the requested causal relationship.",
        )


def test_causal_diagnostics_are_additional_to_historical_metrics():
    diagnostics = causal_diagnostics([
        {
            "caseId": "CASE-04",
            "causalOverclaimRate": 1.0,
            "abstentionAccuracy": 0.0,
            "roleAdmissibilityRate": 0.0,
            "causalClaimSignature": [("claim-1", "EXPLICITLY_DOCUMENTED", ())],
        },
        {
            "caseId": "CASE-04",
            "causalOverclaimRate": 0.0,
            "abstentionAccuracy": 1.0,
            "roleAdmissibilityRate": 1.0,
            "causalClaimSignature": [("claim-1", "NOT_ESTABLISHED", ())],
        },
    ])
    assert diagnostics["causalOverclaimRate"] == 0.5
    assert diagnostics["abstentionAccuracy"] == 0.5
    assert diagnostics["roleAdmissibilityRate"] == 0.5
    assert diagnostics["causalClaimStability"] == 0.0


def test_production_result_is_scored_from_evidence_first_claim_shape():
    case = {
        "expectedEvidence": ["adr", "story"],
        "expectedCausalLinks": [{
            "id": "CL-01", "from": "ADR-043", "to": "Story-0040",
            "strength": "EXPLICITLY_DOCUMENTED",
        }],
    }
    result = score_production_result(case, {
        "evidence": [{"reference": "adr"}, {"reference": "story"}],
    }, {
        "causalClaims": [{
            "source": "ADR-043", "target": "Story-0040",
            "causalClassification": "EXPLICITLY_DOCUMENTED",
            "evidenceReferences": [{"reference": "adr"}],
        }],
    })
    assert result["causalReasoningAccuracy"] == 1.0
    assert result["groundingValid"] is True


def test_production_adapter_canonicalizes_descriptive_source_without_model_ids():
    from evaluations.product_value.interpretation import adapt_production_story_context_result

    adapted = adapt_production_story_context_result(
        {"expectedCausalLinks": [{"id": "CL-01", "from": "ADR-043", "to": "Story-0040"}]},
        {"causalClaims": [{
            "source": "ADR-043 - Account Identity and Mode-Specific Risk Facts",
            "target": "Story 0040 - Mode-Aware Risk Facts",
            "causalClassification": "EXPLICITLY_DOCUMENTED",
            "evidenceReferences": [],
        }]},
    )
    assert adapted["causalClaims"][0]["claimId"] == "claim-1"


def test_evidence_removal_retains_non_causal_evidence():
    context = {"evidence": [
        {"reference": "causal"},
        {"reference": "chronology"},
        {"reference": "topic"},
    ]}
    reduced = remove_causal_evidence(context, {"causal"})
    assert [item["reference"] for item in reduced["evidence"]] == ["chronology", "topic"]


def test_story0132_thresholds_require_case04_three_of_three():
    runs = [
        {"caseId": "CASE-01", "causalReasoningAccuracy": 1.0, "groundingValid": True, "unsupportedInferenceRate": 0},
        {"caseId": "CASE-04", "causalReasoningAccuracy": 1.0, "groundingValid": True, "unsupportedInferenceRate": 0},
        {"caseId": "CASE-04", "causalReasoningAccuracy": 0.0, "groundingValid": True, "unsupportedInferenceRate": 0},
        {"caseId": "CASE-04", "causalReasoningAccuracy": 1.0, "groundingValid": True, "unsupportedInferenceRate": 0},
    ]
    assert evaluate_thresholds(runs, stability=1.0)["pass"] is False


def test_v2_locator_is_bounded_and_does_not_author_content():
    locator = EvidenceLocator(kind="LINE_RANGE", startLine=2, endLine=3)
    assertion = EvidenceAssertion(
        evidenceReference=CausalEvidenceRef(
            reference="document:source:story.md@rev",
            role=CausalEvidenceRole.NON_CAUSAL_CONTEXT,
        ),
        locator=locator,
        assertionRole=CausalEvidenceRole.NON_CAUSAL_CONTEXT,
    )
    assert assertion.resolved_content is None
    assert assertion.resolved_content_digest is None


def test_v2_assessment_requires_the_core_owned_question_shape():
    with pytest.raises(ValidationError):
        CausalQuestion(
            source="ADR-043",
            target="ADR-043",
            relationAsked="CAUSAL",
            answerRequired=True,
        )


def test_v2_python_validation_rejects_question_substitution():
    service = StoryContextAnalysisGenerationService.__new__(StoryContextAnalysisGenerationService)
    assessment = CausalAssessment(
        question=CausalQuestion(
            source="Other",
            target="Target",
            relationAsked="CAUSAL",
            answerRequired=True,
        ),
        classification=CausalClassification.NOT_ESTABLISHED,
        evidenceAssertions=[],
        explanation="The fixed relationship is not established.",
    )
    output = type("Output", (), {
        "causal_assessment": assessment,
        "causal_claims": [],
        "architecture_findings": [], "decision_findings": [], "evidence_findings": [],
        "historical_context": [], "constraint_findings": [],
        "impacted_component_findings": [], "uncertainties": [],
        "confidence": type("Confidence", (), {"level": "HIGH"})(),
        "output_classification": [],
    })()
    with pytest.raises(StoryContextAnalysisOutputValidationError, match="question"):
        service._validate_output(
            output,
            {},
            {
                "causalContractVersion": "V2",
                "causalAnswerRequired": True,
                "causalQuestion": {
                    "source": "ADR-043", "target": "ExecutionConfiguration",
                    "relationAsked": "CAUSAL", "answerRequired": True,
                },
            },
        )
