# AgentMon — Remote Monitor & Control for Claude Code / Codex Sessions

> **Hand this file to Claude (Claude Code or claude.ai) as the single source of truth.**
> Build everything from scratch: desktop agent, mobile app, protocol, security, and UI.
> Whenever a step needs something only the owner can do (Cloudflare, DNS, Firebase, installing tools), **STOP and tell the owner exactly what to do** (see the `USER ACTION` blocks and Section 3). Do not silently assume these are done.

---

## 1. Idea in one paragraph

Developers run an AI coding agent (Claude Code, Codex) inside Android Studio's terminal, give it a long task, and walk away. The agent may stall (permission prompt, error, question, finished and idle) with nobody there. **AgentMon** is a mobile app (Kotlin + Jetpack Compose) that connects securely to the developer's computer and lets them:

1. See the live status of every agent session (running / waiting for input / error / idle / done).
2. Read what the agent is doing (recent activity timeline, last message, terminal mirror).
3. Send an instruction or answer a prompt from the phone, delivered into the same project's agent session.

Each session is **private to the devices the developer paired**. No one else can see or control it.

## 2. Goals and non-goals

**Goals**
- Free or near-free to run. No rented servers.
- Minimal setup on the laptop (one small app + one background tunnel) and on the phone (just the app).
- Per-device privacy: only paired phones, only the projects they were granted.
- Premium, polished UI on both mobile and desktop.

**Non-goals (v1)**
- Not an Android Studio plugin (a background desktop app is enough for v1).
- No multi-user cloud accounts, no central backend.
- No screen sharing / file browser.

## 3. Manual setup the OWNER must do (Claude: prompt the owner for each)

Claude must present these as a checklist at the right moment and wait for confirmation.

### 3.1 Domain and Cloudflare (owner has `appsdev.qzz.io`)
1. Confirm the domain's DNS is managed by Cloudflare (the domain's nameservers point to Cloudflare). If it isn't, set the Cloudflare nameservers at the DigitalPlat domain dashboard.
2. Decide the hostname: **`agent.appsdev.qzz.io`** (used everywhere below).
3. On the laptop install `cloudflared` and run:
   ```
   cloudflared tunnel login
   cloudflared tunnel create agentmon
   cloudflared tunnel route dns agentmon agent.appsdev.qzz.io
   ```
4. Create `config.yml` (Claude should generate the exact file for the owner's OS and paths):
   ```yaml
   tunnel: agentmon
   credentials-file: <path to the generated <TUNNEL-ID>.json>
   ingress:
     - hostname: agent.appsdev.qzz.io
       service: http://localhost:8787
     - service: http_status:404
   ```
5. Install as an auto-start service: `cloudflared service install` (Windows service / systemd / launchd).
6. Optional hardening: Cloudflare Zero Trust → Access (free plan) in front of the hostname. Note that this adds a login layer that native apps must handle via service tokens, so treat it as optional after v1 works.

### 3.2 Laptop prerequisites
- JDK 17+ (only needed for development; the shipped desktop app is bundled with a runtime via `jpackage`).
- Claude Code installed and logged in. Codex support is optional.
- Android Studio (already present).

### 3.3 Phone / development
- Android Studio to build the mobile app, and a phone with developer mode (or an emulator).

### 3.4 Optional, Phase 7 only: push notifications
- A Firebase project (free Spark plan) with Android app registered; owner downloads `google-services.json`. See Section 11.

## 4. Architecture

```
┌──────────────┐  wss://agent.appsdev.qzz.io/ws   ┌────────────────────┐
│ Mobile app   │ ───────────────────────────────► │ Cloudflare edge    │
│ Compose+Ktor │ ◄─────────────────────────────── │ (TLS, routing)     │
└──────────────┘                                  └─────────┬──────────┘
                                                            │ cloudflared tunnel (outbound from laptop)
                                          ┌─────────────────▼─────────────────┐
                                          │ Laptop                            │
                                          │  Desktop Agent (Kotlin/JVM)       │
                                          │   ├ Ktor server :8787 (WS + HTTP) │
                                          │   ├ Hook receiver  /hook (local)  │
                                          │   ├ Session registry (SQLite)     │
                                          │   ├ Injector (PTY / headless)     │
                                          │   └ Device registry + allow-list  │
                                          │  Claude Code hooks ──POST──► /hook│
                                          └───────────────────────────────────┘
```

Key point: **the laptop connects outward** to Cloudflare, so no router ports, no public IP. Cloudflare only carries bytes. Authorization is enforced by the desktop agent using device keys (Section 6).

## 5. Tech stack

| Layer | Choice | Notes |
|---|---|---|
| Language | Kotlin everywhere | Shared protocol module via Kotlin Multiplatform |
| Mobile UI | Jetpack Compose, Material 3, Navigation Compose | MVVM + StateFlow, Koin for DI |
| Mobile networking | Ktor Client (CIO) + WebSockets, kotlinx.serialization | Auto-reconnect with backoff |
| Mobile storage | DataStore (settings), Android Keystore (device key) | Private key must be non-exportable |
| Desktop app | Compose for Desktop (tray icon + small windows) | Packaged with `jpackage` |
| Desktop server | Ktor Server (CIO) + WebSockets + ContentNegotiation | Bind `127.0.0.1` only |
| Desktop storage | SQLDelight (SQLite) | Sessions, devices, allow-list, audit log |
| PTY | **pty4j** (JetBrains) | Cross-platform, including Windows ConPTY |
| QR | ZXing (generate on desktop, scan on mobile with ML Kit or CameraX + ZXing) | |
| Crypto | JDK `java.security` on desktop; Android Keystore on mobile | ECDSA P-256 (see 6.1) |
| Tunnel | cloudflared named tunnel | Free |

Project layout (Gradle multi-module):
```
agentmon/
  shared/       # KMP: protocol models, envelope, crypto helpers, constants
  desktop/      # Compose Desktop app + Ktor server + hook receiver + injector
  mobile/       # Android app
  docs/         # this spec, protocol.md, threat-model.md
```

## 6. Security and privacy (non-negotiable)

### 6.1 Keys
- Each **phone** generates an **ECDSA P-256** keypair in the Android Keystore (hardware-backed where available, non-exportable). P-256 is chosen because Keystore support is universal, whereas Ed25519 support is only recent on Android.
- The **desktop** has its own P-256 keypair, stored in a local file protected by OS user permissions (or the OS credential store if practical).

### 6.2 Pairing (one time per phone)
1. Owner clicks "Pair new device" in the desktop app. The agent creates a **one-time pairing token** (128-bit random, expires in 2 minutes, single use) and shows a QR containing: `{ host: "agent.appsdev.qzz.io", token, desktopPubKeyFingerprint }`.
2. Phone scans the QR and calls `POST /pair` with `{ token, devicePublicKey, deviceName }`.
3. **The desktop shows an approval dialog** ("Approve *Pixel 8*?") with the grant options: which projects this device may see/control, and whether it may **send input** or is **read-only**. Nothing is stored until the owner approves on the laptop.
4. Desktop responds with its public key. The phone **pins** it and verifies that the fingerprint matches the QR.

### 6.3 Every connection (mutual authentication)
1. Phone opens `wss://…/ws` and sends `hello { deviceId }`.
2. Desktop replies `challenge { nonce, desktopSignature }`. The phone verifies the desktop's signature with the pinned key (prevents impersonation, including by anyone on the path).
3. Phone signs the nonce and sends `auth { signature }`. Desktop verifies it against the stored device public key.
4. Only then does the connection join the **allow-listed project channels** for that device.
- Nonces are single-use with a 30-second expiry. Rate-limit failed auths per IP and per deviceId. Log to the audit table.

### 6.4 Isolation rules
- Every event is routed by `projectId`. The router sends an event **only** to connections whose device allow-list contains that project.
- Devices marked read-only get `403 READ_ONLY` on any `send_input`.
- Owner can **revoke** any device instantly from the desktop UI (connection is dropped, key deleted).
- The `/hook` endpoint listens on `127.0.0.1` only and requires a local shared secret (a random token placed in the hook command) so other local processes can't spoof events.
- Never log prompt or terminal contents to disk unless the owner enables it. Truncate payloads sent to phone (e.g. last N KB).

### 6.5 Later hardening (Phase 8)
- Optional end-to-end encryption of payloads (ECDH → AES-GCM) so even Cloudflare's edge sees only ciphertext at the app layer.
- Optional Cloudflare Access service-token layer.

## 7. Desktop agent design

### 7.1 Responsibilities
1. Run a Ktor server on `127.0.0.1:8787` (`/ws`, `/pair`, `/hook`, `/health`).
2. Install and maintain Claude Code hook configuration for each monitored project.
3. Maintain the session registry and state machine.
4. Deliver instructions into the right session (Section 7.4).
5. Tray app UI: status, pairing, device management, project list, activity log.

### 7.2 Hook integration (status source)
Claude Code exposes lifecycle hooks configured in `.claude/settings.json` (project) or `.claude/settings.local.json`. A hook is a command that receives a JSON event on stdin. Documented events include `SessionStart`, `UserPromptSubmit`, `PreToolUse`, `PostToolUse`, `Notification`, `Stop`, `StopFailure`, `SessionEnd`.

**Claude must read the current official docs (https://code.claude.com/docs/en/hooks) before implementing** to confirm exact event names and the JSON payload fields (expect `session_id`, `cwd`, `transcript_path`, `hook_event_name`, and event-specific data). Do not trust this file for exact payload shapes.

The desktop app's "Add project" action writes (merging, never overwriting existing user hooks) an entry like this for each event:
```json
{
  "hooks": {
    "Notification": [
      { "hooks": [ { "type": "command",
        "command": "curl -s -X POST http://127.0.0.1:8787/hook -H \"X-Agentmon-Secret: <secret>\" -H \"Content-Type: application/json\" --data-binary @-" } ] }
    ]
  }
}
```
Windows note: `curl` exists on Windows 10+, but quoting differs. Prefer shipping a tiny helper executable `agentmon-hook` that reads stdin and POSTs, so quoting and OS differences are avoided. Use `settings.local.json` so nothing is committed to the repo, and provide an **Uninstall hooks** button.

### 7.3 State machine (per session)
```
SessionStart / UserPromptSubmit / PreToolUse / PostToolUse  → RUNNING
Notification (permission / waiting for input)               → WAITING_INPUT
Stop                                                        → IDLE (finished a turn)
StopFailure                                                 → ERROR
SessionEnd                                                  → ENDED
no events for N minutes while RUNNING                       → STALE (show "possibly stuck")
```
Store: `sessionId, projectId, state, lastEventAt, lastMessageSnippet, lastTool, errorInfo`. Keep a capped timeline of events per session (e.g. last 200) for the phone's activity view.

### 7.4 Sending instructions from the phone — two modes

**Mode A — Wrapper (interactive, works inside Android Studio's terminal). Recommended default.**
The developer starts the agent through a wrapper: `agentmon claude` instead of `claude` (a small CLI shipped with the desktop app). The wrapper opens a **PTY via pty4j**, spawns `claude` inside it, and mirrors the developer's keyboard and screen normally, while also registering the PTY with the desktop agent. The agent can then:
- **Write text + Enter** into the PTY (send an instruction).
- Send **quick-action keystrokes** (approve / deny / interrupt) mapped to the current Claude Code prompt UI.
- Stream a rolling screen buffer to the phone for the "terminal view".

**Honest limitation (document this in the app's onboarding):** it is not reliably possible to inject input into a terminal session that was started *without* the wrapper. Sessions launched directly in Android Studio's terminal can be **monitored** (hooks work) but not **controlled**. The UI should show these as "Monitor only" and explain how to start a controllable session.

**Mode B — Headless resume (robust, for unattended runs).**
For a session that is idle/finished, the agent can run `claude -p --resume <session-id> "<instruction>"` in the project's `cwd` and stream the result (`--output-format stream-json`) to the phone. This needs no terminal tricks. Claude must verify in current docs how headless mode is billed and authenticated (subscription vs API key) and surface that to the owner, because it may differ from interactive mode.

Quick-action keystrokes and prompt detection depend on Claude Code's current UI, so isolate them behind a `PromptProfile` interface with per-version profiles so it can be updated without touching the rest.

### 7.5 Desktop UI (premium, see Section 9)
Tray icon with status colour; main window with: Overview (projects + live session cards), Devices (paired phones, permissions, revoke), Pair (QR), Activity log, Settings (port, hook install/uninstall, tunnel status check, start with OS).

## 8. Protocol

All messages are JSON over a single WebSocket, wrapped in a versioned envelope. Define them once in `shared/` with kotlinx.serialization.

```json
{ "v": 1, "id": "uuid", "type": "session.update", "ts": 1760000000000, "projectId": "…", "payload": { } }
```

**Client → Desktop:** `hello`, `auth`, `subscribe {projectIds}`, `session.list`, `session.history {sessionId, beforeTs?}`, `send_input {sessionId, text}`, `quick_action {sessionId, action: APPROVE|DENY|INTERRUPT|CONTINUE}`, `terminal.attach {sessionId}`, `terminal.detach`, `ping`.

**Desktop → Client:** `challenge`, `ready {projects, sessions}`, `session.update {session}`, `session.event {sessionId, event}`, `terminal.chunk {sessionId, data}`, `ack {ackId}`, `error {code, message}`, `pong`, `revoked`.

**Error codes:** `AUTH_FAILED`, `NOT_PAIRED`, `FORBIDDEN_PROJECT`, `READ_ONLY`, `SESSION_NOT_CONTROLLABLE`, `SESSION_NOT_FOUND`, `RATE_LIMITED`, `BAD_REQUEST`, `INTERNAL`.

**Reliability requirements**
- Heartbeat ping/pong every ~25s (Cloudflare drops idle WebSockets; the app must also reconnect gracefully).
- Reconnect with exponential backoff + jitter. On reconnect, the client resends `subscribe` and the server sends a fresh snapshot.
- `send_input` is acknowledged (`ack`) with a delivery result so the UI can show Sent / Delivered / Failed.
- Cap `terminal.chunk` size and rate; coalesce output.

## 9. UI / UX design brief — make it premium

**The owner explicitly wants a premium, attractive look. Claude: use your own best design judgment, and treat the following as a starting point rather than a ceiling.** Load and follow any frontend/design guidance available. Avoid default-template looks. Consistency, spacing, and motion quality matter more than the number of screens.

### 9.1 Visual language
- **Dark-first**, with a refined light theme. Deep navy/graphite backgrounds, layered surfaces with subtle borders and soft glow, not flat grey boxes.
- Suggested palette (adjust if you find something better):
  - Background `#0B0F1A`, Surface `#121826`, Surface-elevated `#1A2236`, Outline `#26304A`
  - Primary gradient `#7C5CFF → #22D3EE`
  - Status: Running `#22D3EE`, Waiting `#FBBF24`, Done `#34D399`, Error `#F87171`, Stale `#94A3B8`
- Typography: a distinctive pairing (e.g. **Space Grotesk** or **Sora** for headings, **Inter** for body, **JetBrains Mono** for terminal/code). Load via downloadable fonts or bundle them.
- Rounded large-radius cards (20–28dp), generous spacing, edge-to-edge with proper insets.
- Custom app icon and tray icon that reflect the status colours.

### 9.2 Motion and feedback
- Animated **status orb** per session (soft pulse when RUNNING, gentle attention shimmer when WAITING_INPUT, static when done).
- Shimmer skeletons while loading; animated connection-state pill (Connected / Reconnecting… / Offline).
- Smooth shared-element transitions from session card to detail; spring animations for sheets.
- Haptics on send / approve / deny; subtle micro-interactions on buttons.
- Respect the "reduce motion" system setting.

### 9.3 Mobile screens
1. **Onboarding + Pair** — short animated intro, QR scanner with a stylish viewfinder, success animation, device naming.
2. **Home** — connection pill; grouped by computer → project; session cards with status orb, project name, last message snippet, relative time, and a "Needs you" section pinned at the top for `WAITING_INPUT`.
3. **Session detail** — header with status + controls; **activity timeline** (tool calls, notifications, messages) with icons; sticky composer at the bottom with quick-action chips (Approve / Deny / Interrupt / Continue) and a text field, with sent/delivered/failed indicators; "Monitor only" banner when not controllable.
4. **Terminal view** (tab in session detail) — monospace ANSI-coloured live mirror with pinch-to-zoom font size and a jump-to-bottom button.
5. **Devices & Security** — this device's info, connection details, and how to revoke.
6. **Settings** — theme, notification preferences, haptics, about.
- Include empty states, error states, and offline states with tasteful illustrations rather than blank screens.
- Support phones and tablets (adaptive layouts), portrait primarily.

### 9.4 Desktop screens (Compose Desktop)
Same design system as mobile. Sidebar navigation, live session cards, a polished **Pair device** dialog with a large QR and countdown ring, the **Approve device** dialog, Devices table with permission toggles, Activity log with filters, and a tray menu with a coloured status dot.

## 10. Build phases (with acceptance criteria)

| Phase | Deliverable | Done when |
|---|---|---|
| 0 | Repo, Gradle multi-module, shared protocol models, design system (theme, components) on both apps | Both apps build and show a themed empty shell |
| 1 | Ktor server + Ktor client WebSocket on **LAN**, hello/ping/pong, fake session data | Phone shows fake live sessions from the laptop over Wi-Fi |
| 2 | **Owner does Section 3.1** → tunnel live at `agent.appsdev.qzz.io` | Phone on mobile data connects via `wss://agent.appsdev.qzz.io/ws` |
| 3 | Pairing + mutual auth + device registry + allow-list + revoke | Unpaired device is rejected; revoked device is dropped immediately; a device sees only its granted projects (automated tests) |
| 4 | Hook installer + `/hook` receiver + state machine + timeline | A real Claude Code session in a test project appears on the phone with correct states |
| 5 | Wrapper CLI + pty4j injection + quick actions + terminal mirror | Instruction typed on phone reaches a wrapper-started session; approve/deny works on a real permission prompt |
| 6 | Headless resume mode + streaming output | Idle session can be continued from phone with streamed reply |
| 7 | Push notifications (see 11) + foreground reconnect handling | Phone notified when a session enters `WAITING_INPUT` while the app is closed |
| 8 | Hardening: E2E encryption option, Cloudflare Access option, packaging (`jpackage` installers, auto-start), polish | Installer works on a clean machine |

Work phase by phase. After each phase, summarize what works and what the owner needs to test or set up next.

## 11. Push notifications when the app is closed (Phase 7)

The point of the product is to notice a stall while away, so this matters. Two free options; **ask the owner which they prefer**:
- **A. Foreground service** on Android that keeps the WebSocket alive and posts local notifications. Simplest and no third party, but battery-sensitive and may be killed by aggressive OEM battery managers.
- **B. FCM via a tiny Cloudflare Worker (free tier)**: the desktop agent POSTs a signed "needs attention" event to the Worker, which calls FCM. This keeps the Firebase service-account credentials off the laptop. The payload should contain no sensitive content (e.g. "A session needs you"), and the app fetches details after opening.

## 12. Testing

- Unit tests: envelope serialization, state machine transitions, nonce/challenge logic, allow-list routing.
- Integration tests: two fake devices with different grants; assert isolation (device A never receives project B events), revocation, replayed nonce rejected, expired pairing token rejected.
- Manual E2E: real Claude Code session, permission prompt, error case, long idle, laptop sleep/wake, network switch Wi-Fi → mobile data.
- Cross-OS checks for the desktop agent (Windows first if that's the owner's OS; ask).

## 13. Known risks and open questions (Claude: verify, don't guess)

1. Exact hook events and payloads may change between Claude Code versions. Read the current docs and add version tolerance.
2. Prompt detection and quick-action keystrokes depend on Claude Code's UI, so keep them behind `PromptProfile`.
3. Uncontrollable sessions (started without the wrapper) must be clearly labelled.
4. Billing and authentication differences between interactive and headless (`-p`) modes need checking and explaining to the owner.
5. Cloudflare free tunnel is fine for personal use; check current Cloudflare terms for WebSocket and long-lived connection limits.
6. Laptop sleep stops the agent and tunnel, so show clear "computer offline / last seen" state on the phone and consider a keep-awake option while a session is RUNNING.
7. Codex support: hook and injection mechanisms differ, so implement behind an `AgentAdapter` interface (`ClaudeCodeAdapter` first, `CodexAdapter` later).

## 14. Instructions to the AI building this

1. Read this whole file first, then propose the exact module structure and confirm before generating code at scale.
2. Build in the phase order above. Keep the code idiomatic Kotlin, with clean architecture, no deprecated APIs, and coroutines/Flow for all async work.
3. Whenever a step requires the owner (Cloudflare, DNS, tunnel, Firebase, installing Claude Code/JDK, granting permissions), **stop, say precisely what to do, and wait**.
4. Generate real, runnable files: Gradle build files, `config.yml`, hook installer, run scripts. Not just snippets.
5. Deliver a **premium UI** as described in Section 9, and be creative beyond it.
6. Keep security rules in Section 6 intact even if it makes development slightly slower. Never weaken auth to "make it work" and never bind the agent to a public interface.
7. Write short docs as you go: `docs/protocol.md`, `docs/threat-model.md`, `docs/setup.md` (owner's setup steps).
