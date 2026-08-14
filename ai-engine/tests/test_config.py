from app.core.config import Settings


def test_settings_use_dedicated_core_port_by_default(monkeypatch) -> None:
    monkeypatch.delenv("CORE_BASE_URL", raising=False)

    settings = Settings.from_environment()

    assert settings.core_base_url == "http://localhost:18080"


def test_settings_preserve_explicit_core_url(monkeypatch) -> None:
    monkeypatch.setenv("CORE_BASE_URL", "http://core.example:19080")

    settings = Settings.from_environment()

    assert settings.core_base_url == "http://core.example:19080"


def test_settings_default_llm_timeout(monkeypatch) -> None:
    monkeypatch.delenv("LLM_TIMEOUT_SECONDS", raising=False)

    settings = Settings.from_environment()

    assert settings.llm_timeout_seconds == 90.0


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
