from app.core.config import Settings


def test_settings_use_dedicated_core_port_by_default(monkeypatch) -> None:
    monkeypatch.delenv("CORE_BASE_URL", raising=False)

    settings = Settings.from_environment()

    assert settings.core_base_url == "http://localhost:18080"


def test_settings_preserve_explicit_core_url(monkeypatch) -> None:
    monkeypatch.setenv("CORE_BASE_URL", "http://core.example:19080")

    settings = Settings.from_environment()

    assert settings.core_base_url == "http://core.example:19080"


def test_settings_expose_browser_facing_ai_engine_url(monkeypatch) -> None:
    monkeypatch.setenv("AI_ENGINE_PUBLIC_BASE_URL", "https://ai.example")

    settings = Settings.from_environment()

    assert settings.public_base_url == "https://ai.example"


def test_settings_reject_invalid_public_url() -> None:
    settings = Settings(public_base_url="ai-engine:8000")

    try:
        settings.validate()
    except ValueError as error:
        assert "AI_ENGINE_PUBLIC_BASE_URL" in str(error)
    else:
        raise AssertionError("invalid public URL was accepted")


def test_settings_reject_invalid_cors_origin() -> None:
    settings = Settings(cors_origins=("frontend:4200",))

    try:
        settings.validate()
    except ValueError as error:
        assert "AI_ENGINE_CORS_ORIGINS" in str(error)
    else:
        raise AssertionError("invalid CORS origin was accepted")


def test_settings_default_llm_timeout(monkeypatch) -> None:
    monkeypatch.delenv("LLM_TIMEOUT_SECONDS", raising=False)

    settings = Settings.from_environment()

    assert settings.llm_timeout_seconds == 90.0


def test_chatgpt_defaults_match_open_source_flow(monkeypatch) -> None:
    for name in (
        "CHATGPT_AUTHORIZATION_ENDPOINT",
        "CHATGPT_TOKEN_ENDPOINT",
        "CHATGPT_RESOURCE",
        "CHATGPT_ISSUER",
        "CHATGPT_SCOPES",
    ):
        monkeypatch.delenv(name, raising=False)

    settings = Settings.from_environment()

    assert settings.chatgpt_authorization_endpoint == "https://auth.openai.com/api/accounts/authorize"
    assert settings.chatgpt_token_endpoint == "https://auth.openai.com/api/accounts/oauth/token"
    assert settings.chatgpt_resource == "https://api.openai.com/v1"
    assert settings.chatgpt_issuer == "https://auth.openai.com"
    assert settings.chatgpt_jwks_url == "https://auth.openai.com/.well-known/jwks.json"
    assert settings.chatgpt_scopes == (
        "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    )


def test_settings_default_max_retries(monkeypatch) -> None:
    monkeypatch.delenv("LLM_MAX_RETRIES", raising=False)

    settings = Settings.from_environment()

    assert settings.llm_max_retries == 2


def test_settings_custom_max_retries(monkeypatch) -> None:
    monkeypatch.setenv("LLM_MAX_RETRIES", "5")

    settings = Settings.from_environment()

    assert settings.llm_max_retries == 5


def test_settings_reject_negative_max_retries(monkeypatch) -> None:
    monkeypatch.setenv("LLM_MAX_RETRIES", "-1")

    try:
        Settings.from_environment()
        assert False, "Should have raised ValueError"
    except ValueError as e:
        assert "LLM_MAX_RETRIES" in str(e)


def test_gemini_settings_prefer_google_api_key(monkeypatch) -> None:
    monkeypatch.setenv("LLM_PROVIDER", "gemini")
    monkeypatch.setenv("LLM_API_KEY", "openai-key")
    monkeypatch.setenv("GOOGLE_API_KEY", "google-key")

    settings = Settings.from_environment()

    assert settings.llm_provider == "gemini"
    assert settings.llm_api_key == "google-key"


def test_settings_reject_gemini_without_api_key(monkeypatch) -> None:
    monkeypatch.setenv("LLM_PROVIDER", "gemini")
    monkeypatch.delenv("LLM_API_KEY", raising=False)
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    monkeypatch.delenv("GOOGLE_API_KEY", raising=False)

    try:
        Settings.from_environment()
        assert False, "Should have raised ValueError"
    except ValueError as e:
        assert "LLM_API_KEY" in str(e)


def test_anthropic_settings_prefer_dedicated_api_key(monkeypatch) -> None:
    monkeypatch.setenv("LLM_PROVIDER", "anthropic")
    monkeypatch.setenv("LLM_API_KEY", "fallback-key")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "anthropic-key")

    settings = Settings.from_environment()

    assert settings.llm_provider == "anthropic"
    assert settings.llm_api_key == "anthropic-key"


def test_settings_reject_anthropic_without_api_key(monkeypatch) -> None:
    monkeypatch.setenv("LLM_PROVIDER", "anthropic")
    monkeypatch.delenv("LLM_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)

    try:
        Settings.from_environment()
        assert False, "Should have raised ValueError"
    except ValueError as e:
        assert "LLM_API_KEY" in str(e)


def test_anthropic_settings_read_workspace_id(monkeypatch) -> None:
    monkeypatch.setenv("LLM_PROVIDER", "anthropic")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "anthropic-key")
    monkeypatch.setenv("ANTHROPIC_WORKSPACE_ID", "wrkspc_test")

    settings = Settings.from_environment()

    assert settings.anthropic_workspace_id == "wrkspc_test"


def test_openrouter_settings_prefer_dedicated_api_key(monkeypatch) -> None:
    monkeypatch.setenv("LLM_PROVIDER", "openrouter")
    monkeypatch.setenv("LLM_API_KEY", "fallback-key")
    monkeypatch.setenv("OPENROUTER_API_KEY", "openrouter-key")

    settings = Settings.from_environment()

    assert settings.llm_provider == "openrouter"
    assert settings.llm_api_key == "openrouter-key"
