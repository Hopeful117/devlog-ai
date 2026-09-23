from enum import Enum

from pydantic import Field

from app.schemas.ai_task import ContractModel


class AiReferenceType(str, Enum):
    FACT = "FACT"
    OBSERVATION = "OBSERVATION"
    INSIGHT = "INSIGHT"
    ANALYSIS = "ANALYSIS"
    PROJECT = "PROJECT"
    PROJECT_PROFILE = "PROJECT_PROFILE"
    HUMAN_CONTEXT = "HUMAN_CONTEXT"
    ENGINEERING_EVENT = "ENGINEERING_EVENT"
    REPOSITORY_EVIDENCE = "REPOSITORY_EVIDENCE"


class AiReferenceScope(str, Enum):
    ANALYSIS_CONTEXT = "ANALYSIS_CONTEXT"
    PROJECT = "PROJECT"
    SOURCE_REVISION = "SOURCE_REVISION"
    REPOSITORY = "REPOSITORY"


class ProviderAiReference(ContractModel):
    type: AiReferenceType = Field(
        description="Typed namespace. Runtime authorization is checked against Core candidates."
    )
    ref: str = Field(
        min_length=1,
        description="Opaque Core-issued token; it must be copied exactly."
    )
    scope: AiReferenceScope = Field(
        description="Typed identity scope. Runtime authorization requires an exact scope match."
    )
