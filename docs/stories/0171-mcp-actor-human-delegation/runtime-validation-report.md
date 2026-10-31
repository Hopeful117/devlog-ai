# Story 0171 - Runtime Validation Report

## Status

BLOCKED - the runtime validation attempt did not satisfy the Story acceptance
conditions and must not be treated as Story acceptance.

## Attempt

- Date: 2026-10-31
- Environment: local Docker Compose stack, OpenCode CLI and local MCP JAR.
- Intended flow: OpenCode -> MCP -> Core -> ChatGPT-backed user-bound Story Agent.

## Failure

The attempt did not use the configured `openai_chatgpt` provider or a real
ChatGPT OAuth association. It used the deterministic `mock` provider for a
technical/local test identity. Core correctly rejected the Story Agent request
with:

```text
provider is not supported for user-bound Story Agent execution
```

The successful `get_engineering_context` call only proved that MCP transport,
OIDC authentication, project authorization and a delegated context read could
reach Core. It did not prove ChatGPT credential association, user-bound AI
execution, task-scoped binding, callback completion, or Story Agent result
retrieval.

## Methodological Deviation

The call was initiated with `opencode run` and temporary local configuration
changes rather than through the normal OpenCode session using the existing
ChatGPT OAuth configuration. A disposable local Keycloak user and delegation
were created for partial authorization testing. The delegation was revoked and
the disposable user was removed after the test.

This setup is not sufficient evidence that the next normal session will
reproduce the required runtime behavior.

## Repeated OAuth Prompt

The repeated ChatGPT login was caused by the validation procedure creating a
new OAuth connection intent for each attempt. These intents are intentionally
short-lived and single-use, so the browser was sent through the authorization
flow again each time. This was not evidence that the persisted ChatGPT
credential had been lost between those attempts.

The current runtime has a durable AI Engine `/data` volume for the encrypted
credential/OAuth stores, and Core currently reports the ChatGPT association as
`PLAN_USAGE_AUTHORIZED` for the tested human principal. A separate restart-
persistence check remains useful, but it was not the cause of the three login
prompts observed during this validation sequence.

## OAuth Persistence Verification

The real ChatGPT OAuth association was verified before and after an AI Engine
restart without starting a new browser authorization flow:

- encrypted credential store rows before restart: present;
- OAuth session store rows before restart: present;
- AI Engine health after restart: healthy;
- encrypted credential store rows after restart: unchanged;
- OAuth session store rows after restart: unchanged;
- Core association after restart: `chatgpt:PLAN_USAGE_AUTHORIZED`.

This confirms persistence of the ChatGPT credential boundary. It does not make
the short-lived MCP delegation durable; Core must still issue and validate a
bounded delegation for each agent runtime authority window.

## Agent-Owned Runtime Verification

The repository convention `opencode-agent` was provisioned as an agent-owned
DevLog principal. Core explicitly mapped the technical MCP actor to that
principal using `KEYCLOAK_MCP_AGENT_PRINCIPAL_ID`.

Without a human account or delegation token, a fresh OpenCode MCP process then
successfully retrieved engineering context. The agent principal's persisted
ChatGPT OAuth association was selected for Story Agent execution, and the AI
Engine accepted the ChatGPT task submission.

The task still fails afterward on Core constraint
`ck_ai_task_prompt_execution_identity`; that failure is documented separately
and is not an authentication or credential-persistence failure.

## Persistence Constraint Revalidation

The failure was traced to `V57`: it allowed the initial Story Context task
state with null provider/model, but not the subsequent Core-authorized state
where `provider` and `model_identifier` are populated before submission.
Migration `V71__allow_authorized_story_context_execution_identity.sql` adds
that explicit intermediate state while preserving the existing null and fully
identified branches.

After rebuilding and restarting Core, the autonomous Story Agent path reached:

- AI credential verification: HTTP 200;
- AI Engine binding registration: HTTP 201;
- AI task submission: HTTP 202;
- Core callback: HTTP 200;
- terminal task persistence: successful, with `LLM_PROVIDER_ERROR` recorded.

The remaining failure is `ChatGPT response did not complete`. It reproduces
with `LLM_MAX_OUTPUT_TOKENS` set to both 8,000 and 20,000, so increasing the
output limit alone does not resolve it. The provider returns a bounded terminal
diagnostic with code `subscription_sharing_usage_limit_exceeded`. The AI Engine
correctly rejects the incomplete response rather than accepting partial JSON.

The ChatGPT provider now preserves bounded terminal diagnostics from
`response.incomplete`, `response.failed`, and `error` events. It does not log
provider content, prompts, access tokens, or refresh tokens.

## OAuth Reassociation Revalidation

The `opencode-agent` ChatGPT association was reauthorized through the normal
OAuth consent flow using a fresh Core connection intent. Core then reported the
configuration as `READY` after the configuration was re-saved to clear the
previous `PROVIDER_UNAVAILABLE` test status.

After restarting the stale MCP process and invoking a fresh OpenCode runtime,
the Story Agent completed successfully:

- Story Agent execution status: `COMPLETED`;
- Core callback accepted;
- result retrieved by the MCP call;
- no files modified by the runtime test;
- no human delegation token used.

The returned context was explicitly reported as truncated (`60` selected from
`431` candidates) with freshness `UNKNOWN`; this is an expected bounded-context
diagnostic and not an execution failure.

## Revalidation Attempt

A second attempt used the real ChatGPT OAuth flow:

- `LLM_PROVIDER=openai_chatgpt` and `CHATGPT_OAUTH_ENABLED=true` were active.
- The browser OAuth flow completed against ChatGPT.
- Core received the callback and stored the ChatGPT association as
  `PLAN_USAGE_AUTHORIZED`.
- Core configured the human principal with `openai_chatgpt` and
  `gpt-5.6-luna`.
- OpenCode invoked the MCP Story Agent with a short-lived Core delegation.
- Core accepted the AI task and the AI Engine accepted the submission.

The request then failed in Core while persisting the task:

```text
SQLState: 23514
constraint: ck_ai_task_prompt_execution_identity
task_type: STORY_CONTEXT_ANALYSIS
provider: chatgpt
model_identifier: gpt-5.6-luna
```

Migration `V57__allow_story_context_execution_identity.sql` requires the
`STORY_CONTEXT_ANALYSIS` branch of this constraint to keep `provider` and
`model_identifier` null, but the current execution writes ChatGPT identity
values. The MCP request therefore returned HTTP 500 before task completion,
callback completion, or result retrieval.

The ChatGPT configuration test also returned
`PROVIDER_RESPONSE_TOO_LARGE` while reading the provider model catalogue. This
is separate from the blocking persistence failure above; the actual Story
Agent submission reached the AI Engine before the Core constraint failure.

## Conclusion

Story 0171 remains unvalidated for human acceptance, but the runtime validation
now proves the real ChatGPT OAuth and MCP agent path through task persistence,
binding, submission, callback, and successful result retrieval after OAuth
reassociation. The earlier subscription-sharing limit was resolved by the new
association; no acceptance criterion is promoted automatically by this report.
No acceptance criterion is promoted to runtime-validated status.

## Required Revalidation

The next attempt must:

1. Use the real `openai_chatgpt` provider.
2. Use an existing human ChatGPT OAuth association through the supported UI or
   documented API flow.
3. Use the normal OpenCode MCP configuration and session path.
4. Verify the complete flow through AI task creation, callback, task-scoped
   binding and result retrieval.
5. Capture only non-sensitive status, correlation and diagnostic evidence.
6. Human review must decide whether the completed runtime evidence is
   sufficient for Story acceptance.
