from app.core.config import Settings


def test_settings_use_dedicated_core_port_by_default(monkeypatch) -> None:
    monkeypatch.delenv("CORE_BASE_URL", raising=False)

    settings = Settings.from_environment()

    assert settings.core_base_url == "http://localhost:18080"


def test_settings_preserve_explicit_core_url(monkeypatch) -> None:
    monkeypatch.setenv("CORE_BASE_URL", "http://core.example:19080")

    settings = Settings.from_environment()

    assert settings.core_base_url == "http://core.example:19080"
