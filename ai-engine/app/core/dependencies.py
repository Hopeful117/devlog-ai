from functools import lru_cache

from app.clients.core_callback_client import CoreCallbackClient
from app.core.config import Settings, get_settings
from app.prompts.insight import InsightPromptBuilder
from app.prompts.deliverable import DeliverablePromptBuilder
from app.prompts.story_context_analysis import StoryContextAnalysisPromptBuilder
from app.providers.base import LlmProvider
from app.providers.mock import MockLlmProvider
from app.providers.openai import OpenAiLlmProvider
from app.providers.gemini import GeminiLlmProvider
from app.providers.anthropic import AnthropicLlmProvider
from app.providers.openrouter import OpenRouterLlmProvider
from app.providers.chatgpt import OpenAiChatGptProvider
from app.services.insight_generation_service import InsightGenerationService
from app.services.task_processing_service import AiTaskProcessingService
from app.services.deliverable_generation_service import DeliverableGenerationService
from app.services.story_context_analysis_generation_service import StoryContextAnalysisGenerationService
from app.prompts.engineering_event import EngineeringEventPromptBuilder
from app.prompts.decision import EngineeringDecisionPromptBuilder
from app.services.engineering_event_generation_service import EngineeringEventGenerationService
from app.services.decision_generation_service import EngineeringDecisionGenerationService
from app.security.credential_store import EncryptedCredentialStore
from app.security.credential_resolver import ProviderCredentialResolver
from app.api.bindings import get_binding_registry


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
            max_retries=settings.llm_max_retries,
        )
    if settings.llm_provider == "openai_chatgpt":
        return OpenAiChatGptProvider(
            resolver=get_credential_resolver(),
            endpoint=settings.chatgpt_responses_endpoint,
            model=settings.llm_model,
            timeout_seconds=settings.llm_timeout_seconds,
            token_endpoint=settings.chatgpt_token_endpoint,
            client_id=settings.chatgpt_client_id,
            resource=settings.chatgpt_resource,
        )
    if settings.llm_provider == "gemini":
        if settings.llm_api_key is None:
            raise ValueError("A Gemini API key is required for the Gemini provider")
        return GeminiLlmProvider(
            api_key=settings.llm_api_key,
            model=settings.llm_model,
            timeout_seconds=settings.llm_timeout_seconds,
            max_output_tokens=settings.llm_max_output_tokens,
            max_retries=settings.llm_max_retries,
        )
    if settings.llm_provider == "anthropic":
        if settings.llm_api_key is None and not settings.anthropic_user_credentials:
            raise ValueError("An Anthropic API key is required for the Anthropic provider")
        return AnthropicLlmProvider(
            api_key=settings.llm_api_key,
            model=settings.llm_model,
            timeout_seconds=settings.llm_timeout_seconds,
            max_output_tokens=settings.llm_max_output_tokens,
            max_retries=settings.llm_max_retries,
            workspace_id=settings.anthropic_workspace_id,
            resolver=get_credential_resolver() if settings.anthropic_user_credentials else None,
        )
    if settings.llm_provider == "openrouter":
        if settings.llm_api_key is None:
            raise ValueError("An OpenRouter API key is required for the OpenRouter provider")
        return OpenRouterLlmProvider(
            api_key=settings.llm_api_key,
            model=settings.llm_model,
            timeout_seconds=settings.llm_timeout_seconds,
            max_output_tokens=settings.llm_max_output_tokens,
            max_retries=settings.llm_max_retries,
        )
    raise ValueError(f"Unsupported LLM provider: {settings.llm_provider}")


@lru_cache
def get_task_processing_service() -> AiTaskProcessingService:
    settings = get_settings()
    callback_client = CoreCallbackClient(
        settings.core_base_url,
        callback_secret=settings.sca_callback_secret,
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
    event_service = EngineeringEventGenerationService(
        provider=build_llm_provider(settings), prompt_builder=EngineeringEventPromptBuilder(),
        callback_client=callback_client)
    decision_service = EngineeringDecisionGenerationService(
        provider=build_llm_provider(settings),
        prompt_builder=EngineeringDecisionPromptBuilder(),
        callback_client=callback_client,
    )
    story_context_analysis_service = StoryContextAnalysisGenerationService(
        provider=build_llm_provider(settings),
        prompt_builder=StoryContextAnalysisPromptBuilder(),
        callback_client=callback_client,
    )
    return AiTaskProcessingService(
        insight_service, callback_client, event_service, decision_service,
        story_context_analysis_service, get_binding_registry()
    )


@lru_cache
def get_credential_store() -> EncryptedCredentialStore:
    settings = get_settings()
    return EncryptedCredentialStore(
        settings.credential_store_path,
        settings.credential_encryption_key,
    )


@lru_cache
def get_credential_resolver() -> ProviderCredentialResolver:
    return ProviderCredentialResolver(get_binding_registry(), get_credential_store())


@lru_cache
def get_deliverable_generation_service() -> DeliverableGenerationService:
    settings = get_settings()
    return DeliverableGenerationService(
        provider=build_llm_provider(settings),
        prompt_builder=DeliverablePromptBuilder(),
    )
