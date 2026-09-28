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
| D→C | `session.removed` | `sessionId`, `projectId` *(addition: ended sessions are forgotten after 1 h, idle ones after 24 h)* |
| D→C | `session.history.result` | `sessionId`, `events` (oldest first), `hasMore` |
| D→C | `terminal.chunk` | `sessionId`, `data` *(phase 5)* |
| D→C | `ack` | `ackId`, `result: DELIVERED/FAILED`, `detail?` |
| D→C | `error` | `code`, `message`, `ackId?` |
| D→C | `pong` / `revoked` | – |

**Addition to the spec:** `session.history.result`. The spec defines the `session.history` request but
no reply type.

Error codes: `AUTH_FAILED`, `NOT_PAIRED`, `FORBIDDEN_PROJECT`, `READ_ONLY`, `SESSION_NOT_CONTROLLABLE`,
`SESSION_NOT_FOUND`, `RATE_LIMITED`, `BAD_REQUEST`, `INTERNAL`.

## Pairing (spec 6.2)

1. The desktop shows a QR code with `AGENTMON1:` + base64url(JSON `{v, url, token, fp, name}`). `token` is
   128-bit, single use and valid for 2 minutes. `fp` is the SHA-256 of the desktop public key.
2. The phone sends `POST /pair {token, devicePublicKey, deviceName, proof}`. `proof` signs
   `agentmon-pair-v1`, `<token>` and `<devicePublicKey>` joined with newlines with the phone's Keystore key.
3. The desktop holds the request for up to 90 s while the owner approves or rejects it and chooses projects
   and read-only or input access. It stores nothing before approval.
4. The reply is `{status, deviceId, desktopPublicKey, computerName, canSendInput}`. The phone pins the
   key only if its fingerprint equals `fp` from the QR, and checks that `deviceId` equals the first 32
   hex characters of its own key fingerprint.
   HTTP status: 200 approved, 403 rejected, 410 invalid or expired, 408 timeout, 429 rate-limited.

## Connection handshake (spec 6.3)

1. The phone sends `hello {deviceId}` within 10 s. An unknown device gets `error NOT_PAIRED` and the socket closes.
2. The desktop sends `challenge {nonce, desktopSignature}`, where the nonce is 32 random bytes used once.
   The signature covers `agentmon-challenge-v1`, `<nonce>` and `<deviceId>` joined with newlines. The phone verifies it with the pinned
   key; if it fails, the phone stops and shows "computer identity changed".
3. The phone replies with `auth {signature}` over `agentmon-auth-v1`, `<nonce>`, `<deviceId>` and `<desktopFingerprint>` joined with newlines
   within 30 s. If it does not verify, the phone gets `error AUTH_FAILED` and the socket closes.
4. The desktop sends `ready` filtered to the granted projects (plus `canSendInput`), then pushes updates for those
   projects only. History for other projects gets `FORBIDDEN_PROJECT`; input from read-only devices gets `READ_ONLY`.
5. Revoking sends `revoked` and closes the socket. Changing a grant closes the socket so the phone reconnects
   with the new grant. `NOT_PAIRED`, `AUTH_FAILED`, `revoked` or a bad desktop signature stop the phone from
   reconnecting until it is paired again.
6. Failed attempts are rate-limited per IP (the `CF-Connecting-IP` header through the tunnel) and per
   device: 5 failures per 5 min.

Heartbeat: the phone sends `ping` every 25 s and treats 60 s without any frame as a dead socket.

## Claude Code hooks → agent (phase 4)

Claude Code posts hook events straight to `http://127.0.0.1:8787/hook` using its built-in **HTTP hook
type**, so there is no helper executable and no shell quoting on Windows. The desktop's **Projects** screen
writes these entries into `<project>/.claude/settings.local.json`, merging with any existing hooks:

```json
{ "type": "http", "url": "http://127.0.0.1:8787/hook", "timeout": 5,
  "headers": { "X-Agentmon-Secret": "<secret from %APPDATA%\AgentMon\hook-secret>" } }
```

They cover `UserPromptSubmit`, `PreToolUse`, `PostToolUse`, `PostToolUseFailure`, `PermissionRequest`,
`Notification`, `Stop`, `StopFailure` and `SessionEnd`. `SessionStart` supports only command hooks, so a
session appears on its first prompt or tool call instead. The agent answers with an empty 204, so Claude sees
"no decision". If the agent is not running, Claude Code treats the failed connection as a non-blocking error
and carries on.

`/hook` refuses a missing or wrong secret, and refuses **any request that came through the Cloudflare
tunnel** (identified by the `CF-Connecting-IP` / `Cf-Ray` headers), because the tunnel also terminates on
127.0.0.1.

State machine: prompt or tool → RUNNING; PermissionRequest or a permission/elicitation notification →
WAITING_INPUT; Stop → IDLE; StopFailure → ERROR; SessionEnd → ENDED; RUNNING with 15 minutes of silence →
STALE.

## Limits

- Timeline: the newest 200 events per session.
- Free text (snippets, event details): truncated to 2,000 characters before it is sent.
- Maximum WebSocket frame: 1 MiB.
