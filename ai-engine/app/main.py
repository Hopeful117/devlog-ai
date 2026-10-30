from fastapi import FastAPI

from app.api.ai_tasks import router as ai_tasks_router
from app.api.health import router as health_router
from app.api.deliverables import router as deliverables_router
from app.api.bindings import router as bindings_router
from app.api.chatgpt_auth import router as chatgpt_auth_router
from app.api.api_key_enrollment import router as api_key_enrollment_router
from app.core.logging import RequestLoggingMiddleware, configure_logging


configure_logging()


def create_app() -> FastAPI:
    application = FastAPI(
        title="DevLog AI Engine",
        version="0.1.0",
    )
    application.add_middleware(RequestLoggingMiddleware)
    application.include_router(health_router)
    application.include_router(ai_tasks_router, prefix="/api/v1")
    application.include_router(deliverables_router, prefix="/api/v1")
    application.include_router(bindings_router, prefix="/api/v1")
    application.include_router(chatgpt_auth_router, prefix="/api/v1")
    application.include_router(api_key_enrollment_router, prefix="/api/v1")
    return application


app = create_app()
