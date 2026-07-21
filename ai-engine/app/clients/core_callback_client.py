from uuid import UUID

import httpx

from app.schemas.ai_task_result import (
    AiTaskResultAcknowledgement,
    AiTaskResultRequest,
)


class CoreCallbackClient:
    def __init__(
        self,
        base_url: str,
        *,
        transport: httpx.AsyncBaseTransport | None = None,
        timeout: float = 5.0,
    ) -> None:
        self._base_url = base_url
        self._transport = transport
        self._timeout = timeout

    async def send_result(
        self,
        correlation_id: UUID,
        result: AiTaskResultRequest,
    ) -> AiTaskResultAcknowledgement:
        if correlation_id != result.correlation_id:
            raise ValueError("Path and payload correlation identifiers must match")

        async with httpx.AsyncClient(
            base_url=self._base_url,
            transport=self._transport,
            timeout=self._timeout,
        ) as client:
            response = await client.post(
                f"/api/v1/ai/tasks/{correlation_id}/result",
                json=result.model_dump(mode="json", by_alias=True),
            )
            response.raise_for_status()

        acknowledgement = AiTaskResultAcknowledgement.model_validate(
            response.json()
        )
        if acknowledgement.correlation_id != correlation_id:
            raise ValueError("Core returned a different correlation identifier")
        if not acknowledgement.acknowledged:
            raise ValueError("Core did not acknowledge the AI task result")
        return acknowledgement
