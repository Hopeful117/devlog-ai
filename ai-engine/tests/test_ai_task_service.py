from datetime import timezone
from uuid import uuid4

from app.models.ai_task import AiTaskType
from app.schemas.ai_task import AiTaskSubmissionRequest
from app.services.ai_task_service import AiTaskAcceptanceService
from tests.intent_fixtures import describe_project_intent, selected_knowledge


def test_acceptance_service_only_acknowledges_submission() -> None:
    correlation_id = uuid4()
    request = AiTaskSubmissionRequest(
        correlation_id=correlation_id,
        request_id=uuid4(),
        task_type=AiTaskType.INSIGHT_GENERATION,
        analysis_id=uuid4(),
        ai_task_id=uuid4(),
        intent=describe_project_intent(),
        selected_knowledge=selected_knowledge(),
        expected_output_contract={"type": "object"},
        metadata={},
    )

    response = AiTaskAcceptanceService().accept(request)

    assert response.correlation_id == correlation_id
    assert response.accepted is True
    assert response.external_job_id is not None
    assert response.accepted_at.tzinfo == timezone.utc
