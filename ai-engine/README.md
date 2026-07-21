# DevLog AI Engine

FastAPI service responsible for the AI-processing boundary of DevLog AI.

The current increment implements health reporting and the ADR-019 task
acceptance contract. It does not execute AI processing, call the Java Core,
send callbacks or start asynchronous workers.

## Local development

```bash
python -m pip install -e ".[dev]"
uvicorn app.main:app --reload
pytest
```

## Endpoints

- `GET /health`
- `POST /api/v1/ai/tasks`
