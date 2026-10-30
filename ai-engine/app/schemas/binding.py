from datetime import datetime
from uuid import UUID

from pydantic import Field

from app.schemas.ai_task import ContractModel


class BindingRegistrationRequest(ContractModel):
    binding_id: UUID = Field(alias="bindingId")
    task_id: UUID = Field(alias="taskId")
    provider: str = Field(min_length=1, max_length=100)
    credential_handle: str = Field(alias="credentialHandle", min_length=1, max_length=255)
    expires_at: datetime = Field(alias="expiresAt")


class BindingRegistrationResponse(ContractModel):
    binding_id: UUID = Field(alias="bindingId")
    task_id: UUID = Field(alias="taskId")
    provider: str
    expires_at: datetime = Field(alias="expiresAt")


class CredentialAssociationVerificationRequest(ContractModel):
    credential_handle: str = Field(alias="credentialHandle", min_length=1, max_length=255)
    provider: str = Field(min_length=1, max_length=100)
    subject: str = Field(min_length=1, max_length=255)


class CredentialAssociationVerificationResponse(ContractModel):
    verified: bool
