from app.prompts.deliverable import DeliverablePromptBuilder
from app.providers.base import LlmProvider
from app.schemas.deliverable import (
    DeliverableGenerationOutput,
    DeliverableGenerationRequest,
    DeliverableGenerationResponse,
)


class DeliverableGenerationService:
    def __init__(self, provider: LlmProvider, prompt_builder: DeliverablePromptBuilder) -> None:
        self._provider = provider
        self._prompt_builder = prompt_builder

    async def generate(self, request: DeliverableGenerationRequest) -> DeliverableGenerationResponse:
        prompt = self._prompt_builder.build(request)
        output = await self._provider.generate_structured(prompt, DeliverableGenerationOutput)
        validated = DeliverableGenerationOutput.model_validate(output)
        return DeliverableGenerationResponse(
            title=validated.title,
            content=validated.content,
            prompt_version=prompt.prompt_version,
            prompt_digest=prompt.content_digest,
            provider=self._provider.provider_name,
            model_identifier=self._provider.model_identifier,
        )
