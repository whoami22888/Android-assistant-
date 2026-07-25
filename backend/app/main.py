from __future__ import annotations

import asyncio
import base64
import json
import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware

from .agent import AgentOrchestrator
from .config import settings
from .database import Database
from .providers import ModelRouter
from .schemas import DeviceResult, PairRequest, PairResponse, TaskRequest, VisionRequest
from .security import authenticated_device, issue_device_token, verify_device_token, verify_pairing_code

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("agent-gateway")
database = Database(settings.database_path)
router = ModelRouter(settings)
orchestrator = AgentOrchestrator(settings, database, router)


@asynccontextmanager
async def lifespan(_: FastAPI):
    if not settings.configured:
        logger.warning("Gateway starts unpaired: configure PAIRING_CODE and TOKEN_SECRET in .env")
    database.initialize()
    yield
    await router.close()


app = FastAPI(title="Consent-first Agent Gateway", version="1.0.0", lifespan=lifespan)
origins = [origin.strip() for origin in settings.cors_origins.split(",") if origin.strip()]
if origins:
    app.add_middleware(CORSMiddleware, allow_origins=origins, allow_credentials=False, allow_methods=["GET", "POST"], allow_headers=["*"])


@app.get("/health")
async def health() -> dict[str, object]:
    return {"ok": True, "configured": settings.configured, "protocol": "agent-gateway/v1"}


@app.post("/v1/pair", response_model=PairResponse)
async def pair(request: PairRequest) -> PairResponse:
    if not settings.configured:
        raise HTTPException(status_code=503, detail="Gateway pairing is not configured")
    if not verify_pairing_code(request.pairing_code):
        raise HTTPException(status_code=401, detail="Invalid pairing code")
    database.upsert_device(request.device_id, request.device_name)
    token, expiry = issue_device_token(request.device_id)
    return PairResponse(access_token=token, expires_at=expiry)


@app.post("/v1/tasks")
async def create_task(request: TaskRequest, device_id: str = Depends(authenticated_device)) -> dict[str, object]:
    result = await orchestrator.run(device_id, request)
    return result.model_dump(exclude_none=True)


@app.post("/v1/tasks/{task_id}/device-result")
async def device_result(task_id: str, payload: DeviceResult, device_id: str = Depends(authenticated_device)) -> dict[str, bool]:
    orchestrator.record_device_result(task_id, {"device_id": device_id, **payload.model_dump()})
    return {"ok": True}


@app.post("/v1/vision/analyze")
async def analyze_frame(request: VisionRequest, device_id: str = Depends(authenticated_device)) -> dict[str, str]:
    # Vision is intentionally opt-in and is never persisted. The current compact gateway uses the text router only.
    if not settings.vision_enabled or not settings.google_api_key:
        raise HTTPException(status_code=403, detail="Vision is disabled. Enable VISION_ENABLED and configure Google API access.")
    try:
        raw = base64.b64decode(request.image_base64, validate=True)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail="Invalid image payload") from exc
    if len(raw) > 1_000_000:
        raise HTTPException(status_code=413, detail="Image frame exceeds the 1 MB privacy limit")
    # A full multimodal call is deliberately left behind the explicit feature flag; no frame is stored or logged.
    return {"message": "Frame accepted for explicit, non-persistent visual analysis. Configure a Gemini vision adapter before enabling in production."}


@app.websocket("/v1/ws")
async def websocket_gateway(websocket: WebSocket) -> None:
    token = websocket.query_params.get("token", "")
    try:
        device_id = verify_device_token(token)
    except HTTPException:
        await websocket.close(code=1008)
        return
    await websocket.accept(subprotocol="agent-gateway.v1")
    await websocket.send_json({"type": "connected", "device_id": device_id})
    try:
        while True:
            incoming = await websocket.receive_json()
            message_type = incoming.get("type")
            if message_type == "task":
                request = TaskRequest.model_validate(incoming.get("payload", {}))
                result = await orchestrator.run(device_id, request)
                if result.action:
                    await websocket.send_json({"type": "device_action", "task_id": result.task_id, "payload": result.action.model_dump(mode="json")})
                await websocket.send_json({"type": "task_result", "payload": result.model_dump(mode="json", exclude_none=True)})
            elif message_type == "device_result":
                task_id = str(incoming.get("task_id", ""))
                payload = DeviceResult.model_validate(incoming.get("payload", {}))
                orchestrator.record_device_result(task_id, {"device_id": device_id, **payload.model_dump()})
                await websocket.send_json({"type": "ack", "task_id": task_id})
            elif message_type == "ping":
                await websocket.send_json({"type": "pong"})
            else:
                await websocket.send_json({"type": "error", "message": "Unsupported message type"})
    except WebSocketDisconnect:
        logger.info("Device disconnected: %s", device_id)
    except Exception as exc:
        logger.exception("WebSocket session error")
        await websocket.send_json({"type": "error", "message": f"Session error: {type(exc).__name__}"})
        await websocket.close(code=1011)
