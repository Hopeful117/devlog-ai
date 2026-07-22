from dataclasses import dataclass
from functools import lru_cache
import os


@dataclass(frozen=True)
class Settings:
    llm_provider: str = "mock"
    llm_model: str = "gpt-4.1-mini"
    llm_api_key: str | None = None
    llm_timeout_seconds: float = 30.0
    llm_max_output_tokens: int = 2_000
    core_base_url: str = "http://localhost:8080"
    core_callback_timeout_seconds: float = 5.0
    core_callback_max_attempts: int = 5
    core_callback_initial_delay_ms: int = 100
    core_callback_max_delay_ms: int = 1_000

    @classmethod
    def from_environment(cls) -> "Settings":
        settings = cls(
            llm_provider=os.getenv("LLM_PROVIDER", "mock").lower(),
            llm_model=os.getenv("LLM_MODEL", "gpt-4.1-mini"),
            llm_api_key=os.getenv("LLM_API_KEY") or None,
            llm_timeout_seconds=float(os.getenv("LLM_TIMEOUT_SECONDS", "30")),
            llm_max_output_tokens=int(
                os.getenv("LLM_MAX_OUTPUT_TOKENS", "2000")
            ),
            core_base_url=os.getenv("CORE_BASE_URL", "http://localhost:8080"),
            core_callback_timeout_seconds=float(
                os.getenv("CORE_CALLBACK_TIMEOUT_SECONDS", "5")
            ),
            core_callback_max_attempts=int(
                os.getenv("CORE_CALLBACK_MAX_ATTEMPTS", "5")
            ),
            core_callback_initial_delay_ms=int(
                os.getenv("CORE_CALLBACK_INITIAL_DELAY_MS", "100")
            ),
            core_callback_max_delay_ms=int(
                os.getenv("CORE_CALLBACK_MAX_DELAY_MS", "1000")
            ),
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        if self.llm_provider not in {"mock", "openai"}:
            raise ValueError("LLM_PROVIDER must be 'mock' or 'openai'")
        if self.llm_provider == "openai" and not self.llm_api_key:
            raise ValueError("LLM_API_KEY is required when LLM_PROVIDER=openai")
        if self.llm_timeout_seconds <= 0:
            raise ValueError("LLM_TIMEOUT_SECONDS must be positive")
        if self.llm_max_output_tokens <= 0:
            raise ValueError("LLM_MAX_OUTPUT_TOKENS must be positive")
        if self.core_callback_timeout_seconds <= 0:
            raise ValueError("CORE_CALLBACK_TIMEOUT_SECONDS must be positive")
        if self.core_callback_max_attempts <= 0:
            raise ValueError("CORE_CALLBACK_MAX_ATTEMPTS must be positive")
        if self.core_callback_initial_delay_ms < 0:
            raise ValueError("CORE_CALLBACK_INITIAL_DELAY_MS must not be negative")
        if self.core_callback_max_delay_ms < self.core_callback_initial_delay_ms:
            raise ValueError(
                "CORE_CALLBACK_MAX_DELAY_MS must be greater than or equal to "
                "CORE_CALLBACK_INITIAL_DELAY_MS"
            )


@lru_cache
def get_settings() -> Settings:
    return Settings.from_environment()
