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
    type: AiReferenceType
    ref: str = Field(min_length=1)
    scope: AiReferenceScope
