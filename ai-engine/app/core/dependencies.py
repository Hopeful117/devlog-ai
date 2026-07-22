from functools import lru_cache

from app.clients.core_callback_client import CoreCallbackClient
from app.core.config import Settings, get_settings
from app.prompts.insight import InsightPromptBuilder
from app.prompts.deliverable import DeliverablePromptBuilder
from app.providers.base import LlmProvider
from app.providers.mock import MockLlmProvider
from app.providers.openai import OpenAiLlmProvider
from app.services.insight_generation_service import InsightGenerationService
from app.services.task_processing_service import AiTaskProcessingService
from app.services.deliverable_generation_service import DeliverableGenerationService


def build_llm_provider(settings: Settings) -> LlmProvider:
    if settings.llm_provider == "mock":
        return MockLlmProvider()
    if settings.llm_provider == "openai":
        if settings.llm_api_key is None:
            raise ValueError("LLM_API_KEY is required for the OpenAI provider")
        return OpenAiLlmProvider(
            api_key=settings.llm_api_key,
            model=settings.llm_model,
            timeout_seconds=settings.llm_timeout_seconds,
            max_output_tokens=settings.llm_max_output_tokens,
        )
    raise ValueError(f"Unsupported LLM provider: {settings.llm_provider}")


@lru_cache
def get_task_processing_service() -> AiTaskProcessingService:
    settings = get_settings()
    callback_client = CoreCallbackClient(
        settings.core_base_url,
        timeout=settings.core_callback_timeout_seconds,
        max_attempts=settings.core_callback_max_attempts,
        initial_delay_ms=settings.core_callback_initial_delay_ms,
        max_delay_ms=settings.core_callback_max_delay_ms,
    )
    insight_service = InsightGenerationService(
        provider=build_llm_provider(settings),
        prompt_builder=InsightPromptBuilder(),
        callback_client=callback_client,
    )
    return AiTaskProcessingService(insight_service, callback_client)


@lru_cache
def get_deliverable_generation_service() -> DeliverableGenerationService:
    settings = get_settings()
    return DeliverableGenerationService(
        provider=build_llm_provider(settings),
        prompt_builder=DeliverablePromptBuilder(),
    )
