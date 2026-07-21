from fastapi import FastAPI

from app.api.ai_tasks import router as ai_tasks_router
from app.api.health import router as health_router


def create_app() -> FastAPI:
    application = FastAPI(
        title="DevLog AI Engine",
        version="0.1.0",
    )
    application.include_router(health_router)
    application.include_router(ai_tasks_router, prefix="/api/v1")
    return application


app = create_app()
