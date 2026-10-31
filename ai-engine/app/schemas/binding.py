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
    subject: str | None = Field(default=None, max_length=255)
    authentication_method: str = Field(alias="authenticationMethod", default="OAUTH")
    intent_id: UUID | None = Field(alias="intentId", default=None)
    principal_binding: str | None = Field(alias="principalBinding", default=None, max_length=255)


class CredentialAssociationVerificationResponse(ContractModel):
    verified: bool
    state: str = "IDENTITY_AUTHORIZED"
    scopes: str = ""


class CredentialRevocationRequest(ContractModel):
    credential_handle: str = Field(alias="credentialHandle", min_length=1, max_length=255)
    provider: str = Field(min_length=1, max_length=100)


class ConfigurationTestRequest(ContractModel):
    provider: str = Field(min_length=1, max_length=100)
    authentication_method: str = Field(alias="authenticationMethod", min_length=1, max_length=32)
    model: str = Field(min_length=1, max_length=255)
    credential_handle: str | None = Field(alias="credentialHandle", default=None, max_length=255)
