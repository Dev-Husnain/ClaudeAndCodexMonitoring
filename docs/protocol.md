# AgentMon protocol

The protocol is JSON over a single WebSocket (`/ws`). It is defined once in
`shared/src/main/kotlin/.../protocol/` and used by both the phone and the desktop agent.

## Envelope

```json
{ "v": 1, "id": "uuid", "type": "session.update", "ts": 1760000000000, "projectId": "p1", "payload": { } }
```

- `type` is the `@SerialName` of the `Message` subtype. `ProtocolCodec` moves it out of the payload.
- A receiver rejects frames whose `v` is newer than it understands, and skips frames it cannot decode
  without closing the socket. Unknown payload fields are ignored, so newer senders stay compatible.

## Messages

| Direction | type | payload |
|---|---|---|
| C→D | `hello` | `deviceId`, `appVersion` |
| C→D | `auth` | `signature` *(phase 3)* |
| C→D | `subscribe` | `projectIds` *(phase 3 allow-list)* |
| C→D | `session.list` | – |
| C→D | `session.history` | `sessionId`, `beforeTs?` |
| C→D | `send_input` | `sessionId`, `text` *(phase 5)* |
| C→D | `quick_action` | `sessionId`, `action: APPROVE/DENY/INTERRUPT/CONTINUE` *(phase 5)* |
| C→D | `terminal.attach` / `terminal.detach` | `sessionId` *(phase 5)* |
| C→D | `ping` | – |
| D→C | `challenge` | `nonce`, `desktopSignature` *(phase 3)* |
| D→C | `ready` | `computer`, `projects`, `sessions` |
| D→C | `session.update` | `session` |
| D→C | `session.event` | `sessionId`, `event` |
| D→C | `session.history.result` | `sessionId`, `events` (oldest first), `hasMore` |
| D→C | `terminal.chunk` | `sessionId`, `data` *(phase 5)* |
| D→C | `ack` | `ackId`, `result: DELIVERED/FAILED`, `detail?` |
| D→C | `error` | `code`, `message`, `ackId?` |
| D→C | `pong` / `revoked` | – |

**Addition to the spec:** `session.history.result`. The spec defines the `session.history` request but
no reply type.

Error codes: `AUTH_FAILED`, `NOT_PAIRED`, `FORBIDDEN_PROJECT`, `READ_ONLY`, `SESSION_NOT_CONTROLLABLE`,
`SESSION_NOT_FOUND`, `RATE_LIMITED`, `BAD_REQUEST`, `INTERNAL`.

## Current handshake (phase 1)

1. The phone connects and sends `hello` within 10 s. Anything else gets `error BAD_REQUEST` and the
   socket closes.
2. The desktop sends `ready` (a full snapshot), then pushes every `session.update` and `session.event`.
3. The phone sends `ping` every 25 s. If no frame arrives for 60 s, the phone treats the socket as dead.
4. On reconnect the phone gets a fresh `ready` and asks again for history of any open session.

Phase 3 inserts `challenge` / `auth` between steps 1 and 2 (spec §6.3).

## Limits

- Timeline: the newest 200 events per session.
- Free text (snippets, event details): truncated to 2,000 characters before it is sent.
- Maximum WebSocket frame: 1 MiB.
