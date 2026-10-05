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
| C→D | `away.set` | `enabled` *(addition, phase 5a)* |
| C→D | `sessions.past` | `projectId` *(addition, phase 6)* |
| C→D | `session.resume` | `projectId`, `claudeSessionId`, `text` *(phase 6; needs input permission; `ack.detail` = session to open)* |
| C→D | `terminal.attach` / `terminal.detach` | `sessionId` / – *(phase 5b; one terminal per connection)* |
| C→D | `terminal.key` | `sessionId`, `key: ENTER/ESCAPE/TAB/SHIFT_TAB/UP/DOWN/CTRL_C/DIGIT_1..3` *(addition, phase 5b; needs input permission)* |
| C→D | `ping` | – |
| D→C | `challenge` | `nonce`, `desktopSignature` *(phase 3)* |
| D→C | `ready` | `computer`, `projects`, `sessions`, `canSendInput`, `awayMode` |
| D→C | `away.update` | `enabled` *(addition, phase 5a; sent to every connected phone)* |
| D→C | `session.update` | `session` (incl. `claudeSessionId` when it differs from `sessionId`, and `title`, the conversation's topic, once known) |
| D→C | `sessions.past.result` | `projectId`, `sessions[{claudeSessionId, title, lastActiveAt}]` *(addition, phase 6)* |
| D→C | `session.event` | `sessionId`, `event` |
| D→C | `session.removed` | `sessionId`, `projectId` *(addition: ended sessions are forgotten after 1 h, idle ones after 24 h)* |
| D→C | `session.history.result` | `sessionId`, `events` (oldest first), `hasMore` |
| D→C | `terminal.screen` | `sessionId`, `columns`, `lines[{spans[{text, fg?, bg?, bold, italic, underline, dim, inverse}]}]` *(replaces the spec's `terminal.chunk`, see below)* |
| D→C | `ack` | `ackId`, `result: DELIVERED/QUEUED/FAILED`, `detail?` |
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

## Remote control through hooks (phase 5a, "Away mode")

Works for every hooked session, with no wrapper. `send_input`, `quick_action` and `away.set` need a device
granted **Allow sending input**; otherwise the desktop answers `error READ_ONLY`. Every request is answered
with an `ack` carrying the request's envelope id.

- **Away mode off** (default, and after every desktop restart): every hook is answered at once, so Claude
  behaves exactly as without AgentMon.
- **Away mode on:** a `PermissionRequest` hook is held (hook timeout 3600 s, held for at most 50 min) and the
  session gets `awaiting {kind: PERMISSION, detail, sinceMs}`. `quick_action APPROVE` answers
  `{"hookSpecificOutput":{"hookEventName":"PermissionRequest","decision":{"behavior":"allow"}}}`; DENY answers
  `behavior: deny` with a message, and INTERRUPT the same with `interrupt: true`.
- A `Stop` hook is held the same way with `awaiting {kind: REPLY, detail: Claude's last message}`.
  `send_input` answers `{"hookSpecificOutput":{"hookEventName":"Stop","additionalContext":"…"}}`, so Claude
  continues with the text; `quick_action CONTINUE` sends "Continue."; DENY/INTERRUPT lets it stop.
- `send_input` while Claude is working returns `QUEUED`; the text is delivered at the next `Stop`, even with
  Away mode off.
- Turning Away mode off, a timeout or `SessionEnd` releases everything held with "no decision": Claude shows
  its normal prompt on the computer.
- Registry changes and their `session.update` broadcasts happen under one lock, so phones always end on
  the current state.

## Resume saved conversations (phase 6)

- **History.** `sessions.past` lists a monitored project's saved conversations from Claude Code's transcripts
  (`~/.claude/projects/<cwd with non-alphanumerics replaced by "-">/<id>.jsonl`, or under `CLAUDE_CONFIG_DIR`;
  subfolder projects confirmed by the recorded `cwd`, matched case-insensitively). Newest 30, kept 30 days by
  Claude Code. Only a title is read: the session name (`agent-name`), else Claude's `ai-title`, else the first
  typed prompt. The format is internal to Claude Code, so it is parsed defensively and nothing else is used.
- **Resume.** `session.resume` (or `send_input` to an ENDED session) runs
  `claude -p --resume <id> --output-format stream-json --verbose` in the conversation's folder, with the prompt on
  **stdin** (never on the command line, so phone text cannot become shell syntax). It uses the owner's normal Claude
  login and plan limits (not `--bare`, which would need an API key). `--resume` keeps the session id, so the run's
  hooks report progress as usual; a wrapper terminal's conversation is shown under the wrapper session.
- A conversation is resumed only while no Claude has it open (ENDED, or a failed phone-started run): two
  processes on one conversation would interleave its transcript. Otherwise the answer is FAILED with a reason.
- While a phone-started run works, its permission prompts wait for the phone even with Away mode off; Stop ends
  the process. Its output is read only to detect an error result and is never stored.
- **Remove from this phone** is phone-only: the app stores `sessionId → last activity time (computer clock)` and
  hides the session until the computer reports newer activity. Nothing is sent to the computer.

## Stop from the phone

`quick_action INTERRUPT` stops Claude in every situation:

| Situation | What the agent does | `ack` |
|---|---|---|
| A permission request is held (Away mode) | answers it `deny` with `interrupt: true` | DELIVERED |
| Claude finished and is held (Away mode) | lets it stop | DELIVERED |
| Wrapper session | presses Esc in the terminal (interrupts at once) and marks the session idle, since an interrupt fires no hook | DELIVERED |
| Any other running hooked session | remembers the request and answers the session's **next hook** with `{"continue": false, "stopReason": "Stopped from the phone (AgentMon)."}`, which stops Claude entirely (tool hooks even mid-response) | QUEUED, "Claude stops at its next step" |
| Session idle or ended | nothing | FAILED |

A request is dropped when the turn ends by itself (Stop, StopFailure, SessionEnd) or after 30 minutes, so it can
never stop a later turn. The phone shows **Stop Claude** (with a confirmation) while a session is running or
waiting and this phone may send input.

## Wrapper and terminal mirror (phase 5b)

`agentmon claude [args]` (the `:cli` module) runs Claude Code in a pseudo-terminal (ConPTY on Windows),
passes the keyboard and screen through unchanged, and connects to `ws://127.0.0.1:8787/wrapper`. That
route has the same rules as `/hook`: the hook secret in `X-Agentmon-Secret`, and anything carrying
Cloudflare headers is refused. When the agent is not running Claude still works; the link retries every
3 s and replays the last ~200k characters of output when it connects.

Wrapper frames (JSON, `type` discriminator, never sent to phones):

| Direction | type | payload |
|---|---|---|
| W→D | `hello` | `wrapperId` (UUID), `cwd`, `columns`, `rows` |
| W→D | `output` | `data` (terminal output, UTF-8) |
| W→D | `resize` | `columns`, `rows` |
| W→D | `exit` | `code` |
| D→W | `input` | `data` (typed into Claude's terminal as is) |

- **One terminal = one session.** The wrapper id is the session id. The session appears as soon as the
  wrapper connects from a monitored folder, so the phone can send the first prompt. Claude gets
  `AGENTMON_WRAPPER_ID` in its environment, and the installed hooks send it back in `X-Agentmon-Wrapper`
  (`allowedEnvVars: ["AGENTMON_WRAPPER_ID"]`). Hooks with that header are filed under the wrapper's
  session instead of Claude's own session id, including after `/clear`. Without the wrapper the variable is
  unset and the header is empty.
- Wrapper sessions are `WRAPPER` controlled. `send_input` types the text, then Enter 120 ms later as its own
  keystroke; multi-line text is sent as a bracketed paste when Claude enabled it. With no hook held,
  `quick_action` presses keys in Claude's own dialog (`ClaudeCodePromptProfile`: APPROVE = Enter,
  DENY/INTERRUPT = Esc), and APPROVE/DENY only while the session is waiting for input.
- **Terminal mirror.** The agent feeds the output into a headless terminal emulator (JediTerm) and sends
  attached phones `terminal.screen`: the newest 200 lines (scrollback plus screen), at most every 250 ms and
  only when something changed. Raw `terminal.chunk` output is not usable because Claude Code redraws with
  cursor movement. Colours are `null` (default), `0..255` (xterm palette), or `1<<24 | 0xRRGGBB`.
  Read-only devices may watch; `terminal.key` and input need "Allow sending input". Terminal content is kept
  in memory only and never logged.
- When Claude exits, the session is marked ENDED. When only the link drops (for example the agent restarts),
  the session falls back to `HOOKS` until the wrapper reconnects.

## Limits

- Timeline: the newest 200 events per session.
- Free text (snippets, event details): truncated to 2,000 characters before it is sent.
- Maximum WebSocket frame: 1 MiB.
