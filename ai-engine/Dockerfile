FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN groupadd --system devlog \
    && useradd --system --gid devlog --home-dir /app devlog
RUN mkdir -p /data && chown -R devlog:devlog /data

COPY pyproject.toml README.md ./
COPY app/ app/

RUN pip install --no-cache-dir .

USER devlog

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
