from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.ai_tasks import router as ai_tasks_router
from app.api.health import router as health_router
from app.api.deliverables import router as deliverables_router
from app.api.bindings import router as bindings_router
from app.api.chatgpt_auth import router as chatgpt_auth_router
from app.api.api_key_enrollment import router as api_key_enrollment_router
from app.api.configuration_test import router as configuration_test_router
from app.core.logging import RequestLoggingMiddleware, configure_logging


configure_logging()


def create_app() -> FastAPI:
    from app.core.config import get_settings

    settings = get_settings()
    application = FastAPI(
        title="DevLog AI Engine",
        version="0.1.0",
    )
    application.add_middleware(
        CORSMiddleware,
        allow_origins=list(settings.cors_origins),
        allow_credentials=False,
        allow_methods=["POST", "OPTIONS"],
        allow_headers=["Content-Type"],
    )
    application.add_middleware(RequestLoggingMiddleware)
    application.include_router(health_router)
    application.include_router(ai_tasks_router, prefix="/api/v1")
    application.include_router(deliverables_router, prefix="/api/v1")
    application.include_router(bindings_router, prefix="/api/v1")
    application.include_router(chatgpt_auth_router, prefix="/api/v1")
    application.include_router(api_key_enrollment_router, prefix="/api/v1")
    application.include_router(configuration_test_router, prefix="/api/v1")
    return application


app = create_app()
