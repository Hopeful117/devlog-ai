FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

RUN groupadd --system devlog \
    && useradd --system --gid devlog --home-dir /app devlog

COPY pyproject.toml README.md ./
COPY app/ app/

RUN pip install --no-cache-dir .

USER devlog

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
