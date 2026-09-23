# Story 0144 - Docker V1 Network Access for Agent Presence

## Status

`IMPLEMENTED - AWAITING HUMAN ACCEPTANCE`

## Scope

Make the physical Agent Presence device reliably reachable from the actual
`devlog-backend` Docker container for V1, while preserving the stable mDNS
identity `agent-presence.local` for host diagnostics and future discovery.

This Story owns local Docker/runtime infrastructure only. It does not own
DevLog domain behavior, `AgentCommunicationPort`, S141 semantics, or device
authentication.

## Problem

The Linux host can resolve the physical ESP32-S3 through its working Avahi/mDNS
stack, but Docker's embedded resolver cannot resolve `.local` names. The
currently active `devlog-backend` container can reach the device by its
diagnostic DHCP address, but not by `agent-presence.local`.

The device already publishes the logical hostname through firmware mDNS. The
remaining boundary is:

```text
host mDNS capability -> Docker DNS resolution -> devlog-backend
```

## Goal

Use the Livebox DHCP reservation as the V1 Docker endpoint so the real
`devlog-backend` container can:

```text
POST http://192.168.1.11/message
-> receive HTTP 200 MESSAGE RECEIVED
-> render the message on the physical OLED
```

with:

```text
DEVICE_BASE_URL=http://192.168.1.11
```

The reserved address is deployment/runtime configuration, not a DevLog domain
identity or a static IP configured on the device.

## Architectural Boundary

```text
DHCP                = device address assignment
mDNS                = device identity and discovery
DHCP reservation     = V1 Docker endpoint stability
DEVICE_BASE_URL     = DevLog configuration boundary
```

The reservation must not be implemented in DevLog domain logic,
`AgentCommunicationPort`, Agent Presence business behavior, MQTT, a broker, a
registry, or a custom discovery protocol.

## Proven Baseline

The physical ESP32-S3 has been flashed and validated with:

```text
DHCP address       = 192.168.1.11 (diagnostic observation only)
mDNS hostname      = agent-presence.local
DNS-SD service     = _http._tcp on port 80
host HTTP by name  = 200 MESSAGE RECEIVED
OLED rendering     = validated
```

The endpoint requires the existing payload fields `source`, `title`, `body`,
and `level`.

## Acceptance Criteria

1. The ESP32 continues to use DHCP and receives the reserved address
   `192.168.1.11` after reboot or reconnect.
2. The ESP32 advertises `agent-presence.local` via mDNS after reboot.
3. The Linux host resolves `agent-presence.local` to `192.168.1.11` and can
   POST `/message` by hostname.
4. The actual `devlog-backend` container can POST `/message` to
   `http://192.168.1.11` and receives `200 MESSAGE RECEIVED`.
5. The physical OLED renders a message delivered from the container.
6. Reserved-IP HTTP delivery survives an ordinary backend restart and device
   reconnect.
7. Active DevLog runtime configuration uses the externally supplied
   `DEVICE_BASE_URL=http://192.168.1.11`.
8. No DevLog Java, Python, frontend, adapter, or S141 business code changes
   are required.
9. The implementation does not use `network_mode: host`, static `/etc/hosts`
   entries, `extra_hosts`, manual IP discovery, or a static IP configured on
   the ESP32.

## Investigation Boundary

The following options were evaluated against the current Linux and Docker
runtime:

```text
Docker embedded DNS alone              -> does not forward host mDNS
backend network_mode: host             -> does not expose host mDNS/NSS
host dnsmasq mDNS forwarding           -> requires privileged CAP_NET_RAW
systemd-resolved mDNS forwarding       -> host mDNS is currently disabled;
                                           enabling it requires host resolver policy
Docker macvlan dnsmasq bridge          -> requires LAN-specific network/IP
                                           allocation and did not resolve in the
                                           current runtime probe
```

The explicit V1 decision is to use the Livebox DHCP reservation. Docker mDNS
bridging is not part of V1 and must not be reopened by this Story.

## V1 Decision

```text
V1_DOCKER_NETWORK_STRATEGY = DHCP_RESERVATION
RESERVED_IP                = 192.168.1.11
MDNS_PRESERVED             = YES
```

The decision is appropriate for the single physical V1 device because the
existing `DEVICE_BASE_URL` boundary already allows deployment-specific runtime
configuration, while transparent Docker mDNS would require disproportionate
privileged infrastructure. The DHCP reservation does not replace mDNS:

```text
DHCP reservation -> stable Docker endpoint for V1
mDNS             -> stable human/device LAN identity and future discovery
```

## Explicit Non-Goals

- No static IP configuration on the ESP32.
- No Device Registry or multi-device routing.
- No MQTT, broker, service mesh, Kubernetes, cloud discovery, or custom
  discovery protocol.
- No authentication, TLS, or `/message` security changes.
- No DevLog application or S141 semantic changes.
- No broad Linux resolver redesign.

## Implementation Notes

The firmware mDNS spike is retained as the production device capability:

```text
Wi-Fi station connects
-> DHCP obtains address
-> mDNS responder starts once after GOT_IP
-> agent-presence.local and _http._tcp are advertised
```

No static device address is part of the firmware identity.

## Runtime Configuration

The physical V1 runtime uses:

```text
DEVICE_BASE_URL=http://192.168.1.11
```

The value is external Compose environment configuration. It is not hardcoded
in DevLog application code.

## S141 Relationship

The network contract is ready for the final S141 autonomous E2E. This Story
does not run that E2E and does not change S141 semantics.

## Security Boundary

```text
DHCP reservation != authentication
mDNS != authentication
```

The unauthenticated LAN-only `/message` endpoint remains outside this Story.
