from dataclasses import dataclass
from functools import lru_cache
import os


@dataclass(frozen=True)
class Settings:
    llm_provider: str = "mock"
    llm_model: str = "gpt-5.6-luna"
    llm_api_key: str | None = None
    anthropic_workspace_id: str | None = None
    anthropic_user_credentials: bool = False
    llm_timeout_seconds: float = 90.0
    llm_max_output_tokens: int = 2_000
    llm_max_retries: int = 2
    core_base_url: str = "http://localhost:18080"
    sca_callback_secret: str = ""
    core_callback_timeout_seconds: float = 5.0
    core_callback_max_attempts: int = 5
    core_callback_initial_delay_ms: int = 100
    core_callback_max_delay_ms: int = 1_000
    service_auth_secret: str | None = None
    service_auth_max_clock_skew_seconds: int = 60
    binding_store_path: str = "data/ai-bindings.sqlite3"
    credential_store_path: str = "data/ai-credentials.sqlite3"
    credential_encryption_key: str | None = None
    chatgpt_oauth_enabled: bool = False
    chatgpt_client_id: str | None = None
    chatgpt_authorization_endpoint: str = "https://auth.openai.com/api/accounts/authorize"
    chatgpt_token_endpoint: str = "https://auth.openai.com/api/accounts/oauth/token"
    chatgpt_responses_endpoint: str = "https://api.openai.com/v1/responses"
    chatgpt_redirect_uri: str | None = None
    chatgpt_scopes: str = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    chatgpt_resource: str = "https://api.openai.com/v1"
    chatgpt_issuer: str = "https://auth.openai.com"
    chatgpt_jwks_url: str = "https://auth.openai.com/.well-known/jwks.json"
    chatgpt_agent_name: str = "DevLog AI"
    chatgpt_ext_agent_host_id: str | None = None
    oauth_store_path: str = "data/ai-oauth.sqlite3"
    oauth_transaction_ttl_seconds: int = 300

    @classmethod
    def from_environment(cls) -> "Settings":
        llm_provider = os.getenv("LLM_PROVIDER", "mock").lower()
        llm_api_key = os.getenv("LLM_API_KEY") or None
        if llm_provider == "gemini":
            llm_api_key = (
                os.getenv("GEMINI_API_KEY")
                or os.getenv("GOOGLE_API_KEY")
                or llm_api_key
            )
        if llm_provider == "anthropic":
            llm_api_key = os.getenv("ANTHROPIC_API_KEY") or llm_api_key
        if llm_provider == "openrouter":
            llm_api_key = os.getenv("OPENROUTER_API_KEY") or llm_api_key
        service_auth_secret = os.getenv("DEVLOG_SERVICE_AUTH_SECRET") or os.getenv("SCA_CALLBACK_SECRET")
        settings = cls(
            llm_provider=llm_provider,
            llm_model=os.getenv("LLM_MODEL", "gpt-5.6-luna"),
            llm_api_key=llm_api_key,
            anthropic_workspace_id=os.getenv("ANTHROPIC_WORKSPACE_ID") or None,
            anthropic_user_credentials=os.getenv("ANTHROPIC_USER_CREDENTIALS", "false").lower() == "true",
            llm_timeout_seconds=float(os.getenv("LLM_TIMEOUT_SECONDS", "90")),
            llm_max_output_tokens=int(
                os.getenv("LLM_MAX_OUTPUT_TOKENS", "2000")
            ),
            llm_max_retries=int(os.getenv("LLM_MAX_RETRIES", "2")),
            core_base_url=os.getenv("CORE_BASE_URL", "http://localhost:18080"),
            sca_callback_secret=service_auth_secret or "",
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
            service_auth_secret=service_auth_secret,
            service_auth_max_clock_skew_seconds=int(
                os.getenv("DEVLOG_SERVICE_AUTH_MAX_CLOCK_SKEW_SECONDS", "60")
            ),
            binding_store_path=os.getenv(
                "DEVLOG_BINDING_STORE_PATH", "data/ai-bindings.sqlite3"
            ),
            credential_store_path=os.getenv(
                "DEVLOG_CREDENTIAL_STORE_PATH", "data/ai-credentials.sqlite3"
            ),
            credential_encryption_key=os.getenv("DEVLOG_CREDENTIAL_ENCRYPTION_KEY") or None,
            chatgpt_oauth_enabled=os.getenv("CHATGPT_OAUTH_ENABLED", "false").lower() == "true",
            chatgpt_client_id=os.getenv("CHATGPT_CLIENT_ID") or None,
            chatgpt_authorization_endpoint=os.getenv(
                "CHATGPT_AUTHORIZATION_ENDPOINT",
                "https://auth.openai.com/api/accounts/authorize",
            ),
            chatgpt_token_endpoint=os.getenv(
                "CHATGPT_TOKEN_ENDPOINT",
                "https://auth.openai.com/api/accounts/oauth/token",
            ),
            chatgpt_responses_endpoint=os.getenv(
                "CHATGPT_RESPONSES_ENDPOINT", "https://api.openai.com/v1/responses"
            ),
            chatgpt_redirect_uri=os.getenv("CHATGPT_REDIRECT_URI") or None,
            chatgpt_scopes=os.getenv(
                "CHATGPT_SCOPES",
                "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct",
            ),
            chatgpt_resource=os.getenv("CHATGPT_RESOURCE", "https://api.openai.com/v1"),
            chatgpt_issuer=os.getenv("CHATGPT_ISSUER", "https://auth.openai.com"),
            chatgpt_jwks_url=os.getenv(
                "CHATGPT_JWKS_URL",
                "https://auth.openai.com/.well-known/jwks.json",
            ),
            chatgpt_agent_name=os.getenv("CHATGPT_AGENT_NAME", "DevLog AI"),
            chatgpt_ext_agent_host_id=os.getenv("CHATGPT_EXT_AGENT_HOST_ID") or None,
            oauth_store_path=os.getenv("CHATGPT_OAUTH_STORE_PATH", "data/ai-oauth.sqlite3"),
            oauth_transaction_ttl_seconds=int(
                os.getenv("CHATGPT_OAUTH_TRANSACTION_TTL_SECONDS", "300")
            ),
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        if self.llm_provider not in {"mock", "openai", "openai_chatgpt", "gemini", "anthropic", "openrouter"}:
            raise ValueError("LLM_PROVIDER must be 'mock', 'openai', 'openai_chatgpt', 'gemini', 'anthropic', or 'openrouter'")
        if self.llm_provider in {"openai", "gemini", "openrouter"} and not self.llm_api_key:
            raise ValueError(f"LLM_API_KEY is required when LLM_PROVIDER={self.llm_provider}")
        if self.llm_provider == "anthropic" and not self.llm_api_key and not self.anthropic_user_credentials:
            raise ValueError("LLM_API_KEY is required when LLM_PROVIDER=anthropic")
        if self.llm_provider == "openai_chatgpt" and not self.chatgpt_oauth_enabled:
            raise ValueError("CHATGPT_OAUTH_ENABLED must be true when LLM_PROVIDER=openai_chatgpt")
        if self.llm_timeout_seconds <= 0:
            raise ValueError("LLM_TIMEOUT_SECONDS must be positive")
        if self.llm_max_output_tokens <= 0:
            raise ValueError("LLM_MAX_OUTPUT_TOKENS must be positive")
        if self.llm_max_retries < 0:
            raise ValueError("LLM_MAX_RETRIES must not be negative")
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
        if self.service_auth_max_clock_skew_seconds <= 0:
            raise ValueError("DEVLOG_SERVICE_AUTH_MAX_CLOCK_SKEW_SECONDS must be positive")
        if not self.binding_store_path.strip():
            raise ValueError("DEVLOG_BINDING_STORE_PATH must not be blank")
        if not self.credential_store_path.strip():
            raise ValueError("DEVLOG_CREDENTIAL_STORE_PATH must not be blank")
        if self.oauth_transaction_ttl_seconds <= 0:
            raise ValueError("CHATGPT_OAUTH_TRANSACTION_TTL_SECONDS must be positive")
        if self.chatgpt_oauth_enabled:
            required = {
                "CHATGPT_AUTHORIZATION_ENDPOINT": self.chatgpt_authorization_endpoint,
                "CHATGPT_TOKEN_ENDPOINT": self.chatgpt_token_endpoint,
                "CHATGPT_REDIRECT_URI": self.chatgpt_redirect_uri,
                "CHATGPT_ISSUER": self.chatgpt_issuer,
                "CHATGPT_EXT_AGENT_HOST_ID": self.chatgpt_ext_agent_host_id,
                "DEVLOG_CREDENTIAL_ENCRYPTION_KEY": self.credential_encryption_key,
                "DEVLOG_SERVICE_AUTH_SECRET": self.service_auth_secret,
            }
            missing = [name for name, value in required.items() if not value]
            if missing:
                raise ValueError("ChatGPT OAuth configuration is incomplete: " + ", ".join(missing))


@lru_cache
def get_settings() -> Settings:
    return Settings.from_environment()
