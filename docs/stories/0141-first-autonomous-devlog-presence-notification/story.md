# Story 0141 - First Autonomous DevLog Presence Notification

## Status

`IMPLEMENTATION BLOCKED - PHYSICAL VALIDATION PENDING`

## Scope

Deliver the first real DevLog-to-physical-device vertical slice using the
existing `AgentCommunicationPort`, HTTP presence adapter, and ESP32
`POST /message` endpoint.

## Boundaries

- DevLog decides whether meaningful analysis content should be communicated.
- `AgentCommunicationPort` remains the application boundary.
- HTTP remains an infrastructure adapter.
- The device remains a generic presence surface.
- Device unavailability must not fail DevLog analysis completion.

## Trigger

```text
completed analysis
  -> CommunicationDecisionService
  -> SPEAK
  -> AnalysisCommunicationUseCase
  -> AgentCommunicationPort
  -> AgentPresenceCommunicationAdapter
  -> HttpAgentPresenceAdapter
  -> POST /message
```

## Device Lifecycle

```text
IDLE
  -> message received -> ACTIVE
  -> button acknowledgement -> IDLE
  -> bounded timeout -> IDLE
```

The initial timeout is ten seconds. Each newly received message starts a new
ten-second presence window.

## Acceptance Criteria

1. The existing device endpoint accepts the existing DevLog payload contract.
2. A received message is rendered with deterministic fixed OLED line bounds.
3. A new message restarts the bounded presence duration.
4. Button acknowledgement clears the display immediately.
5. The display returns to idle after the bounded duration.
6. HTTP delivery, acknowledgement, and timeout paths cannot concurrently
   corrupt display access.
7. DevLog communication and failure-isolation tests remain passing.
8. The device builds with the repository's normal ESP-IDF command.
9. Final acceptance uses a real DevLog analysis and physical device when the
   hardware and local network are available.

## Explicit Non-Goals

- No broker, event bus, orchestrator, discovery, MQTT, WebSocket, SSE, or
  cloud connectivity.
- No authentication implementation.
- No message persistence, retry queue, scheduler, priority engine, simulator,
  audio, LED, or multi-agent orchestration.

## Expected Changes

- Device presence lifecycle and concurrency handling.
- Device rendering bounds based on the existing 128x64 OLED and 5x7 font.
- Runtime configuration for the existing DevLog `DEVICE_BASE_URL` only during
  local integration validation.
