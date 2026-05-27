import aiohttp
import asyncio
from dataclasses import dataclass
from config import BASE_URL


@dataclass
class ApiResponse:
    status: int
    body: dict
    latency_ms: int


class FestinClient:
    def __init__(self, session: aiohttp.ClientSession):
        self.session = session
        self.base_url = BASE_URL

    async def login(self, email: str, nickname: str, role: str = "VISITOR", booth_id: int | None = None) -> str | None:
        payload = {"email": email, "nickname": nickname, "role": role, "managedBoothId": booth_id}
        res = await self._post("/api/v1/auth/login", payload, token=None)
        print(f"  [Login] status={res.status} body={res.body}")
        if res.status in (200, 201):
            return res.body.get("accessToken") or res.body.get("token")
        return None

    async def get_booths(self, token: str) -> ApiResponse:
        return await self._get("/api/v1/booths", token)

    async def enqueue(self, token: str, booth_id: int) -> ApiResponse:
        return await self._post("/api/v1/waitings", {"boothId": booth_id}, token)

    async def get_position(self, token: str, booth_id: int) -> ApiResponse:
        return await self._get(f"/api/v1/waitings/booth/{booth_id}", token)

    async def cancel(self, token: str, booth_id: int) -> ApiResponse:
        return await self._delete(f"/api/v1/waitings/{booth_id}", token)

    async def call_next(self, token: str, booth_id: int) -> ApiResponse:
        return await self._post("/api/v1/waitings/call", {"boothId": booth_id}, token)

    async def confirm_entrance(self, token: str, booth_id: int, waiting_id: int) -> ApiResponse:
        return await self._post(f"/api/v1/booths/{booth_id}/entrance/{waiting_id}", {}, token)

    async def complete_experience(self, token: str, booth_id: int, waiting_id: int) -> ApiResponse:
        return await self._post(f"/api/v1/booths/{booth_id}/complete/{waiting_id}", {}, token)

    # ── 내부 헬퍼 ──────────────────────────────────────────

    async def _get(self, path: str, token: str) -> ApiResponse:
        headers = {"Authorization": f"Bearer {token}"}
        import time
        start = time.monotonic()
        try:
            async with self.session.get(f"{self.base_url}{path}", headers=headers) as r:
                latency = int((time.monotonic() - start) * 1000)
                body = await self._parse_body(r)
                return ApiResponse(status=r.status, body=body, latency_ms=latency)
        except asyncio.TimeoutError:
            return ApiResponse(status=0, body={"error": "timeout"}, latency_ms=int((time.monotonic() - start) * 1000))

    async def _post(self, path: str, payload: dict, token: str | None) -> ApiResponse:
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        import time
        start = time.monotonic()
        try:
            async with self.session.post(f"{self.base_url}{path}", json=payload, headers=headers) as r:
                latency = int((time.monotonic() - start) * 1000)
                body = await self._parse_body(r)
                return ApiResponse(status=r.status, body=body, latency_ms=latency)
        except asyncio.TimeoutError:
            return ApiResponse(status=0, body={"error": "timeout"}, latency_ms=int((time.monotonic() - start) * 1000))

    async def _delete(self, path: str, token: str) -> ApiResponse:
        headers = {"Authorization": f"Bearer {token}"}
        import time
        start = time.monotonic()
        try:
            async with self.session.delete(f"{self.base_url}{path}", headers=headers) as r:
                latency = int((time.monotonic() - start) * 1000)
                body = await self._parse_body(r)
                return ApiResponse(status=r.status, body=body, latency_ms=latency)
        except asyncio.TimeoutError:
            return ApiResponse(status=0, body={"error": "timeout"}, latency_ms=int((time.monotonic() - start) * 1000))

    async def _parse_body(self, response: aiohttp.ClientResponse) -> dict:
        try:
            return await response.json(content_type=None)
        except Exception:
            text = await response.text()
            return {"raw": text}