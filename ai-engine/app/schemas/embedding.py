from pydantic import BaseModel, ConfigDict, Field


class EmbeddingRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    texts: list[str] = Field(min_length=1, max_length=100)
    model: str = Field(min_length=1, max_length=100)


class EmbeddingResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    embeddings: list[list[float]]
    model: str
    dimensions: int
