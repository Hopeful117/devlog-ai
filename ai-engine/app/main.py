from fastapi import FastAPI

app = FastAPI(
    title="DevLog AI Engine",
    version="0.1.0",
)


@app.get("/health", tags=["health"])
def health_check() -> dict[str, str]:
    return {"status": "healthy"}