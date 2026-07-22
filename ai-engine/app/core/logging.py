import contextvars
import json
import logging
import os
import re
import time
from datetime import datetime, timezone
from uuid import uuid4

from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint


CORRELATION_HEADER = "X-Correlation-ID"
correlation_id_context: contextvars.ContextVar[str] = contextvars.ContextVar(
    "correlation_id", default="none"
)
_SAFE_CORRELATION_ID = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        event: dict[str, object] = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "service": "devlog-ai-engine",
            "logger": record.name,
            "correlationId": correlation_id_context.get(),
            "message": record.getMessage(),
        }
        for key in (
            "method", "path", "status", "durationMs", "taskCorrelationId",
            "attempt", "proposalCount", "exceptionType",
        ):
            if hasattr(record, key):
                event[key] = getattr(record, key)
        if record.exc_info:
            event["exception"] = self.formatException(record.exc_info)
        return json.dumps(event, ensure_ascii=False, default=str)


def configure_logging() -> None:
    level_name = os.getenv("LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())

    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level)
    for logger_name in ("uvicorn", "uvicorn.error"):
        logger = logging.getLogger(logger_name)
        logger.handlers = [handler]
        logger.propagate = False
    logging.getLogger("uvicorn.access").disabled = True


class RequestLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(
        self,
        request: Request,
        call_next: RequestResponseEndpoint,
    ) -> Response:
        candidate = request.headers.get(CORRELATION_HEADER, "")
        correlation_id = (
            candidate if _SAFE_CORRELATION_ID.fullmatch(candidate) else str(uuid4())
        )
        token = correlation_id_context.set(correlation_id)
        started_at = time.perf_counter()
        logger = logging.getLogger("app.http")
        response: Response | None = None
        try:
            response = await call_next(request)
            return response
        except Exception:
            logger.exception(
                "Unhandled HTTP request exception",
                extra={
                    "method": request.method,
                    "path": request.url.path,
                    "exceptionType": "unhandled",
                },
            )
            raise
        finally:
            duration_ms = round((time.perf_counter() - started_at) * 1000)
            status = response.status_code if response is not None else 500
            log_method = logger.error if status >= 500 else (
                logger.warning if status >= 400 else (
                    logger.debug if request.url.path == "/health" else logger.info
                )
            )
            log_method(
                "HTTP request completed",
                extra={
                    "method": request.method,
                    "path": request.url.path,
                    "status": status,
                    "durationMs": duration_ms,
                },
            )
            if response is not None:
                response.headers[CORRELATION_HEADER] = correlation_id
            correlation_id_context.reset(token)
