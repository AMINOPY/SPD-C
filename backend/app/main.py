from fastapi import FastAPI
from fastapi.responses import JSONResponse

app = FastAPI(
    title="Spd con API",
    version="0.1.0",
    description="Development scaffold for the Spd con configuration management platform.",
)


@app.get("/health/live", tags=["health"])
def liveness() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/health/ready", tags=["health"])
def readiness() -> JSONResponse:
    # Replace this placeholder with database/cache dependency checks when wired.
    return JSONResponse(content={"status": "development-scaffold", "dependencies": "not-configured"})
