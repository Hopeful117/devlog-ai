# AI Task Domain

## Overview

The AI Task domain represents an AI processing request created and owned by the
Java Core for one `Analysis`.

Each task persists the exact `AnalysisContext` sent to the AI Engine and uses a
Core-generated correlation identifier. The Python AI Engine may execute the
request, but it does not own the business lifecycle stored by the Core.

## Lifecycle

```text
CREATED -> SUBMITTED -> PROCESSING -> COMPLETED
                                 \-> FAILED
                                 \-> TIMED_OUT
```

Transitions are explicit service operations. Arbitrary status updates are not
exposed.

## Responsibilities

The domain is responsible for:

- associating one or more AI tasks with an analysis;
- generating stable correlation identifiers;
- preserving immutable context snapshots;
- tracking submission and processing timestamps;
- assigning a Core-owned execution deadline at submission;
- terminalizing expired submitted or processing tasks through the scheduled
  recovery watchdog;
- recording external job identifiers, attempts, and technical failures;
- enforcing the V1 lifecycle, including the distinct `TIMED_OUT` outcome.

The domain is not responsible for:

- communicating with the Python AI Engine;
- retrying tasks automatically;
- changing the lifecycle of the originating analysis.

ADR-020 callbacks are handled by the application-level AI Engine callback
service. That service coordinates proposal persistence and the task terminal
transition atomically while this domain remains the owner of task state.

Permanent callback contract or domain rejections are recorded and terminalize
the task as `FAILED`. Transient state conflicts remain retryable. The MCP
`POLL_TIMEOUT` response is a caller read timeout and is not equivalent to the
Core-owned `TIMED_OUT` state.

The default task deadline is five minutes and can be configured with
`ai-task.timeout-seconds`. The recovery scan delay defaults to 30 seconds and
can be configured with `ai-task.recovery-delay-ms`. Recovery is processed in
batches controlled by `ai-task.recovery-batch-size`, and migration backfill
assigns the same historical five-minute budget to existing submitted or
processing tasks without a deadline.
