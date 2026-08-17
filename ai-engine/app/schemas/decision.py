from pydantic import BaseModel, ConfigDict, Field


class DecisionOutputModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        extra="forbid",
        str_strip_whitespace=True,
    )


class EngineeringDecisionProposalOutput(DecisionOutputModel):
    title: str = Field(min_length=1, max_length=255)
    context: str = Field(min_length=1, max_length=5000)
    choice: str = Field(min_length=1, max_length=5000)
    rationale: str = Field(min_length=1, max_length=5000)
    consequences: str | None = Field(
        default=None, min_length=1, max_length=5000
    )


class EngineeringDecisionGenerationOutput(DecisionOutputModel):
    proposals: list[EngineeringDecisionProposalOutput] = Field(max_length=10)