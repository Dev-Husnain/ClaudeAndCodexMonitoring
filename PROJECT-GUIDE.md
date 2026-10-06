# AgentMon — Project Guide

## 1. Overview
AgentMon lets a developer watch and (later) steer Claude Code sessions running on their laptop from
their phone. It has three parts: an Android app (`:app`), a desktop agent with a tray UI (`:desktop`),
and a shared wire protocol (`:shared`). Product spec: `AGENT_MONITOR_SPEC.md`. Rules:
`CLAUDE-CODE-GUIDELINES.md`. There is no Figma; the design source is spec §9 (see §2).

## 2. Design Source
Spec §9 visual brief, snapshotted in `design/tokens.md`. Screens were designed in-house from that brief.
Every screen below uses the same source.

## 3. Architecture & Conventions
**Modules**
- `:shared` is plain Kotlin/JVM (Java 11 bytecode) and holds the protocol only: `Message` (sealed),
  `Envelope`, `ProtocolCodec`, DTOs, enums and constants. The spec asks for KMP, but both consumers
  are JVM, so plain JVM avoids AGP 9 + KMP friction (decision D1).
- `:app` is the Android app, package `com.claude.codex.ai.monitoring`.
- `:desktop` is the Compose Desktop tray app plus the Ktor server on 127.0.0.1:8787.
- `:cli` is `agentmon claude`: Claude Code in a pseudo-terminal (pty4j/ConPTY + JLine raw mode), linked to the
  agent on `/wrapper` so the phone can see and type into the terminal (M5b).

**`:app` layering** follows guidelines §5.1: `domain` (pure Kotlin) ← `data` (DataStore, Ktor) and
`presentation` (MVVM). Koin DSL modules live in `di/` (`appModule`, `dataModule`, `domainModule`,
`presentationModule`) and are started in `App`.
- State: one `XUiState` per screen in `_xUiState`/`xUiState`; events via `onEvent(XEvent)`. Loading,
  empty and error are represented as flags on the state.
- Async: the Flow-based repository `AgentRepositoryImpl` owns one WebSocket, starts it while collected
  (`WhileSubscribed(5s)`), reconnects with jittered back-off, and keeps the last snapshot across reconnects.
  State transitions are pure functions in `AgentStateReducer`, which is unit tested.
- Mappers: `data/mapper/ProtocolMappers.kt` (Dto→Model), `presentation/common/SessionUiMappers.kt`
  and `presentation/<feature>/<Feature>UiMapper.kt` (Model→UiModel).
- Text from ViewModels goes through `UiText` (resource, plural or raw) and is resolved in UI with `resolve()`.
- Navigation 3 (`core/navigation`): `Route` sealed keys, `AppNavHost`, and a 320ms slide+fade
  transition in both directions.
- **Insets and system bars (owner rule):** `MainActivity` only calls `enableEdgeToEdge()` and
  `setContent { AppRoot() }`. `AppRoot` resolves the theme mode and hosts the nav graph in a `Box`
  with no padding. `AppTheme` sets `isAppearanceLightStatusBars`/`NavigationBars` from the *selected*
  theme. **Every screen** draws `AuroraBackground` edge-to-edge and applies `systemBarsPadding()`
  (Settings also applies `imePadding()`) to its own content.

**Shared components (`core/ui`)**
- `AuroraBackground`: screen background with glows.
- `SurfaceCard`: layered card with an optional accent.
- `StatusOrb`: pulse or shimmer dot; respects reduce-motion.
- `StatusPill`: orb plus label, used for connection and control state.
- `GradientButton`: primary button with springy press feedback.
- `IconActionButton`: round icon button.
- `AppTopBar`, `SectionHeader`.
- `StateMessage`: empty, error and offline states.
- `InfoBanner`: inline notice.
- `SkeletonBlock`: shimmer placeholder.
- `StatusTone` / `color()`: status vocabulary.

**How to add a screen:** add a `Route` → create `presentation/<feature>/` with UiState, Event,
ViewModel and Screen (Screen = `AuroraBackground` + `systemBarsPadding()` + components) → put each
component in `components/`, one per file with a preview → register the ViewModel in `presentationModule` →
add an `entry<Route.X>` in `AppNavHost` → add strings to `values/strings.xml`.

## 4. Tech Stack & Dependencies
All versions are pinned to releases built against Kotlin ≤ 2.2, so the project's Kotlin 2.2.10 is **not** upgraded.

| Library | Version | Why |
|---|---|---|
| AGP / Kotlin / Compose BOM | 9.4.1 / 2.2.10 / 2026.02.01 | existing template, unchanged |
| Navigation 3 + lifecycle-viewmodel-navigation3 | 1.2.0 / 2.11.0 | guidelines §5.7 |
| Koin (BOM) | 4.1.1 | DI, guidelines §5.5 (**new**) |
| Ktor client CIO + websockets | 3.3.3 | spec §5 phone networking (**new**) |
| Ktor server CIO + websockets | 3.3.3 | spec §5 desktop server (**new**) |
| kotlinx.serialization | 1.9.0 | protocol (**new**) |
| DataStore Preferences | 1.2.1 | settings (**new**) |
| Compose Multiplatform | 1.9.3 | desktop UI (**new**) |
| kotlinx-coroutines-test, kotlin-test-junit | 1.10.2 / 2.2.10 | tests |
| Fonts: Space Grotesk, Inter, JetBrains Mono | fontsource latin TTF | spec §9.1, SIL OFL |
| CameraX (camera2, lifecycle, compose, mlkit-vision) | 1.6.2 | QR scanning on the Pair screen (**new, M3**) |
| ML Kit barcode-scanning (bundled model) | 17.3.0 | QR decoding without Play Services (**new, M3**) |
| SQLDelight (sqlite-driver) | 2.1.0 | desktop device store and audit log, spec §5 (**new, M3**) |
| ZXing core | 3.5.4 | desktop QR generation, spec §5 (**new, M3**) |
| pty4j | 0.13.13 | `agentmon claude` pseudo-terminal (ConPTY on Windows), spec §5 (**new, M5b**) |
| JLine terminal (JNI) | 3.30.17 | raw console input for the wrapper; jline 4 needs Java 22 (**new, M5b**) |
| jediterm-core | 3.72 | headless terminal emulator for the phone's terminal mirror; 3.73+ need Kotlin 2.4, so 3.72 is pinned; from JetBrains' Maven repo, limited to its group in settings (**new, M5b**) |
| slf4j-nop | 2.0.17 | keeps library logging off Claude's screen in the CLI (**new, M5b**) |

## 5. Feature Progress
### Feature: Foundation (M0)
- [x] Git repo, modules, version catalog, Koin, Nav3 shell
- [x] Theme tokens, fonts, dark and light themes, status-bar icons follow the selected theme
- [x] Shared component library with previews
- [x] Custom adaptive app icon (mipmap layer-list fallback for API 24–25)
- [x] Desktop themed window and tray with a status-coloured dot
- [x] Builds clean, committed

### Feature: Live overview (M1, spec phase 1)
- [x] Protocol models and codec, 6 tests
- [x] Desktop `/health` and `/ws` on 127.0.0.1, registry, demo simulator; 10 tests (incl. WebSocket integration)
- [x] Phone WebSocket client: heartbeat, 60s dead-socket timeout, jittered back-off, offline after 3 failures
- [x] Home: needs-you section, grouping by project, loading/empty/offline/stale states
- [x] Session detail: status header and activity timeline (history + live events)
- [x] Settings: theme mode, validated server address (wss required off loopback), haptics, about
- [x] Unit tests: reducer, back-off, overview use case, URL validation, relative time, HomeViewModel (23)
- [x] **Gate:** verified on Pixel_9a emulator (API 36) and Xiaomi M2101K7AG (API 31) via `adb reverse` over wireless adb
Notes: sessions are demo data until phase 4.

### Feature: Pairing + mutual auth (M3, spec phase 3)
- [x] Shared: P-256 helpers, purpose-bound signed payloads, pairing code format (12 tests with codec)
- [x] Desktop: persistent key; SQLDelight devices and audit log; one-time 2-minute tokens; approval dialog
  (projects + read-only/input); challenge/auth with single-use nonces; rate limits; allow-list routing;
  read-only enforcement; instant revoke; Devices and Activity screens (21 tests incl. isolation, replay,
  revocation, expiry, rate limiting)
- [x] Phone: Keystore key (StrongBox first), pinned desktop key with fingerprint check, handshake, stops
  retrying when refused
- [x] Onboarding, Pair (CameraX + ML Kit scanner, paste-code fallback, confirm/waiting/success/error),
  Devices & security (unpair), Home "pair again" states, read-only pill
- [x] Removed the dev-only server-address setting; the QR carries the address now
- [x] Unit tests: parse code, pair flow VM, refusal handling, home unauthorized/read-only (34 app tests)
- [x] **Gate:** on Xiaomi M2101K7AG (API 31) over USB/adb reverse: paired (code entry), approve required
  (nothing is approved without a click, checked for 20 s), matching keys on both screens, live grant change
  (read-only pill disappeared), revoke gives "This phone was removed", re-pair works, and the phone
  signs in again automatically after the desktop restarts. The camera QR scan itself was not tested by me.
Notes: `PairingRepositoryImpl` (key pinning check) has no unit test: it needs the Android Keystore and
DataStore. It is covered by the manual gate above.

### Feature: Real sessions via hooks (M4, spec phase 4)
- [x] Read the current hooks docs (Claude Code 2.1.283): uses the built-in `http` hook type, and `PermissionRequest` for instant "needs you"
- [x] `/hook` endpoint: secret header, tunnel requests refused, empty 204, 2 MiB cap, rate-limited audit of rejections
- [x] Session state machine incl. STALE after 15 min, cleanup of old sessions, `session.removed`
- [x] Projects screen: add (folder picker) / reinstall / remove; merge-safe installer that never overwrites
  user hooks, refuses invalid JSON and gitignores the settings file; per-project "last event" health
- [x] SQLDelight migration v1 → v2 (project table), verified on the owner's existing database
- [x] Tests: state machine (7), installer (6), endpoint (5); 87 tests in total at M4
- [x] **Gate:** a real `claude` session in a monitored folder showed on the Xiaomi with the correct states and
  timeline (Prompt → Read → Finished → Session ended)

### Feature: Control from the phone, Away mode (M5a, spec phase 5 via hooks)
- [x] `ControlCenter`: in Away mode, holds `PermissionRequest` / `Stop` hooks until the phone answers; documented
  `decision` / `additionalContext` replies; queued instructions delivered at the next Stop; release on Away off,
  50-min timeout and SessionEnd
- [x] Protocol: `away.set`, `away.update`, `awaiting` on sessions, `ack` with DELIVERED / QUEUED / FAILED;
  input requires the device's "Allow sending input" grant (READ_ONLY otherwise)
- [x] Phone: Away mode toggle (Home + detail), "Needs you" includes held sessions, awaiting card
  (Approve / Deny / Stop, Continue / Let it stop), composer with delivery note, haptics
- [x] Desktop: Away mode switch in the sidebar, "waiting for your phone" line on session tiles
- [x] Fix: registry state changes and broadcasts are atomic, and the tracker reads/writes a session in one step,
  so a phone can no longer end on a stale copy (`AwayModeFlowTest`)
- [x] Fix: the awaiting card / "Needs you" were inserted above the list's scroll anchor and stayed off-screen;
  the lists now scroll up when something needs the user
- [x] Tests: ControlCenter (7), Away-mode flow + ordering (2), SessionDetailViewModel (6); 102 tests in total
  (app 42, shared 12, desktop 48)
- [x] **Gate:** on the Xiaomi via the tunnel with real `claude -p`: permission approved from the phone → file
  written; reply typed on the phone at Stop → Claude continued, asked again, approved again, edited the file;
  "Let it stop" → Claude exited. Away mode was toggled from the phone.

### Feature: Wrapper + terminal mirror (M5b, spec phase 5)
- [x] `:cli` `agentmon claude`: PTY (ConPTY), raw keyboard pass-through, resize, UTF-8 safe output, finds `claude`
  like the shell (npm `.cmd` shims via `cmd /c claude`), `AGENTMON_CLAUDE` override; runs Claude normally
  when the agent is down and links up later (replaying recent output)
- [x] `/wrapper` WebSocket (hook secret, tunnel refused); one terminal = one session (wrapper id), visible
  before the first prompt; hooks aliased onto it through `X-Agentmon-Wrapper` + `allowedEnvVars`
- [x] Typing into the TUI (Enter as a separate keystroke, bracketed paste for multi-line), `terminal.key`,
  quick actions via `ClaudeCodePromptProfile` when no hook is held
- [x] Terminal mirror: headless JediTerm → `terminal.screen` (200 lines, ≤ 4/s, only on change), only to attached
  phones; read-only phones may watch but not type
- [x] Phone: Activity / Terminal tabs for wrapper sessions, live terminal (colours, follow + "Jump to latest",
  sideways scroll, pinch zoom), keys row, composer types into the terminal; attached only while the tab is open
- [x] Tests: mirror (4), wrapper link incl. security and read-only (4), CLI (5), codec/wrapper frames (2), detail
  VM terminal (3), terminal mapper (2); 122 tests in total (app 47, shared 14, desktop 56, cli 5)
- [x] Verified on this PC: interactive `agentmon claude` in Windows Terminal renders normally, the session
  showed as Controllable before any prompt, and the wrapper re-linked by itself after an agent restart
- [x] **Gate:** on the Xiaomi via the tunnel: the wrapper session appeared before any prompt, the Terminal tab mirrored
  Claude live, a prompt typed on the phone ran (three sleeps, then hello5.txt), and Stop interrupted a long task

### Feature: Stop from the phone
- [x] **Stop Claude** button (with confirmation) on the session screen, both tabs, while Claude runs or waits
- [x] Wrapper sessions: Esc at once, session marked idle (an interrupt fires no hook)
- [x] Every other hooked session: the next hook answers `{"continue": false}` (works without the wrapper and
  without Away mode); the request expires with the turn or after 30 min so it never hits a later turn
- [x] Tests: ControlCenter stop (3), wrapper stop in the link test, detail VM stop (2); 127 tests in total
  (app 49, shared 14, desktop 59, cli 5)
- [x] **Verified on the Xiaomi:** wrapper session stopped mid-story (story.txt never written); a plain `claude -p`
  session stopped at step 14 of 25 and exited

### Feature: Hardening (M8, spec phase 8), first part
- [x] **Stay awake while Claude works** (default on): `SetThreadExecutionState(ES_SYSTEM_REQUIRED)` through JNA on one
  dedicated thread while a session runs, waits or is held, or Away mode is on; the display may still sleep
- [x] **Start with Windows**: per-user `Run` key (written with JNA `Advapi32Util`) → installed copy (`:desktop:installAgent` → `%LOCALAPPDATA%\AgentMon\agent`,
  `javaw` + jars, `--background` starts in the tray); refuses to register a copy running from Gradle's build folders
- [x] "This computer" card on the desktop Overview with both switches; settings in `settings.properties`
- [x] Tests (4): keep-awake decisions, Run-key command, not-installed/other-OS refusal, settings persistence;
  153 tests in total (app 62, shared 17, desktop 72, cli 2)
- [x] `installAgent` verified: folder with AgentMon.cmd and 76 jars incl. the Windows UI runtime (skiko), SQLite, JNA
- [x] Real test on this PC: the installed copy started without Gradle; "Start with Windows" wrote the Run key (first
  attempt failed: `reg.exe` rejected the quoted value, now written through the Windows API); the exact Run-key
  command started the agent in the tray with no window; the phone connected to it, a new `agentmon claude` terminal
  showed up, and a prompt typed on the phone created the file on the PC
- [ ] Not seen yet: an actual sign-in after a reboot
- [ ] Remaining, needs the owner: an MSI/EXE installer needs a JDK with `jpackage` (the Android Studio JBR has none);
  optional end-to-end encryption on top of the tunnel's TLS; a TalkBack/large-font QA pass on the phone

### Feature: Readable project and session names
- [x] Project names come from the project itself: Android Studio's `.idea/.name`, Gradle `rootProject.name`,
  `package.json`, `Cargo.toml`, `pyproject.toml`, else the folder; slugs read as words (`agentmon-hooktest` →
  "Agentmon Hooktest"), names with their own capitals stay. Re-read at every agent start, so older projects update
- [x] Each session carries its topic (`SessionDto.title`): the first typed prompt until Claude's title is in the
  transcript (read at every `Stop`, only from Claude's projects folder); `/clear` starts over. Shown under the project
  name on the card and as the detail screen's subtitle; never in notifications (prompt text stays off the lock screen)
- [x] No raw ids on the phone: an unnamed project or session reads "Claude Code session" (was a hash / short id)
- [x] Tests: namer (3), topic (2), transcript title (1); all suites and lint pass
- [x] Real test: the phone shows "AGENTMON HOOKTEST" after an agent restart
- [x] `agentmon claude` in a folder that is not monitored adds it (hooks + `.gitignore` line, audited), so a terminal in
  any Android Studio project shows up; never the home folder, a folder above it, or a drive root. Phones limited to
  chosen projects still need the grant. Real test: a wrapper in ClaudeMonitoring appeared after an agent restart

### Away-mode replies continue Claude again (v1.0.6)
- [x] A reply typed on the phone after Claude finished (held `Stop`) showed "Delivered to Claude", but Claude stayed
  stopped. The hook answered with `hookSpecificOutput.additionalContext` only, which current Claude Code treats as an
  end-of-turn note. It now answers with the documented Stop decision `{"decision":"block","reason":…}`, so Claude
  continues with the instruction. The same reply serves queued messages and the Continue button. Tests updated

### Terminal scrollback and copy (v1.0.5)
- [x] The phone's terminal only scrolled about one screen. Windows' ConPTY repaints the visible screen instead of
  scrolling, so lines leaving the top never reached JediTerm's history (measured: an 80-line answer kept only the last 30
  rows). `TerminalMirror` now keeps its own scrollback (1,000 lines): every change is compared with the previous screen,
  and when the content moved up by `k` lines (at least 3 matching non-empty lines), the `k` lines that left the top are
  saved. Real emulator scrolling (macOS/Linux) still uses JediTerm's own history. Resizes reset the comparison
- [x] `terminal.screen` carries the newest 500 lines (was 200)
- [x] Phone: long-press selects terminal text (SelectionContainer); a **Copy** chip copies the whole terminal
- [x] Tests: ConPTY-style repaint keeps scrolled-off lines, a bottom-only redraw is not scrolling, shift detection (3)

### Fixes found in real use (v1.0.4)
- [x] Pairing used a USB code whenever this computer could not reach its own tunnel. Only the computer's DNS was failing,
  and a Samsung without adb could not pair ("could not reach your computer"). The tunnel route is now used whenever an
  address is set; the check from this computer only warns. Real test: the A26 paired through the tunnel while this
  computer's DNS still failed
- [x] A read-only phone saw a terminal without keys or message box and no reason; the Terminal tab now shows the
  read-only note too
- [x] README and docs/tunnel.md: pairing behaviour and three new troubleshooting rows

### Feature: README restructure + v1.0.2
- [x] README: quick start, then numbered steps (install computer, install phone, choose USB or own tunnel incl.
  adb/USB-debugging setup, pair, start Claude), running/updating/uninstalling the agent, everyday use
- [x] Released v1.0.2 (forwarding-header detection + docs); phone updated and connected through the tunnel

### Feature: Own-tunnel guide
- [x] `docs/tunnel.md`: a general guide for any owner: requirements for any tunnel, Cloudflare step by step (incl. the
  Windows service ImagePath fix), ngrok and Tailscale Funnel (marked untested), what does not work (port forwarding,
  http, Cloudflare Access), setting the address, re-pairing, changing it later, security, troubleshooting.
  README links to it; `docs/setup.md` is marked as this machine's log
- [x] Tunnel traffic is now also recognised by standard forwarding headers (`X-Forwarded-For`, `X-Real-IP`,
  `Forwarded`, `X-Forwarded-Host`), so hooks and the wrapper link stay local-only behind any tunnel, and rate
  limits use the real client address. Test (1)

### Feature: Clear the activity log (v1.0.1)
- [x] Activity tab: **Clear activity** with an inline confirmation; deletes every entry and records one
  "Activity log cleared" entry, so a wipe is never silent. Test (1). Real test: button and confirmation shown
- [x] Published releases: v1.0.0, then v1.0.1 with this change (a published tag is never moved)

### Feature: Release packaging + README
- [x] `README.md`: install, pairing, daily use (incl. Android Studio terminal), tunnel, phone and desktop guides,
  security, troubleshooting, build and release steps
- [x] `scripts/package-release.ps1` → `dist/`: `AgentMon-<v>-windows-x64.zip` (`AgentMon\AgentMon.exe` and
  `cligentmon.exe`, jpackage app-images with a jlink-trimmed runtime, ~136 MB zipped) and the debug-signed APK.
  Needs a JDK with jpackage (`JPACKAGE_JDK`); the wrapper lives in `cli\` because Windows folder names ignore case
- [x] "Start with Windows" also registers the packaged `AgentMon.exe` (`jpackage.app-path`)
- [x] **Phone access address** on Overview → This computer: the owner's tunnel hostname, validated (https only, host
  only), saved in `settings.properties`; `--public-url` / `AGENTMON_PUBLIC_URL` override it and lock the field. The
  owner's domain is no longer built in: without an address, pairing offers USB only and says where to set it.
  This PC's address was saved during the switch, so nothing changed here
- [x] Real test: the packaged agent answered `/health`, the packaged wrapper ran `claude --version` through ConPTY
- [ ] Owner: upload the two files to a GitHub release (no `gh` CLI on this PC); a store APK needs an owner signing key

### Feature: Markdown replies + complete removal
- [x] Claude's replies render as Markdown on the phone (own parser in `core/utils/Markdown.kt`, no library: current
  Markdown libraries need Kotlin > 2.2): headings, lists, quotes, fenced code and tables (scroll sideways), links
  (http/https only), inline bold/italic/code/strike; selectable. Used for the latest reply on the detail screen and
  the Away-mode reply card. Cards and timeline show plain previews (`stripMarkdown`); commands stay as typed
- [x] The computer now keeps Claude's full last reply (up to 2,000 characters) instead of its first line
- [x] Removing a session also hides its conversation in History (by Claude's id, with 5 min slack for the transcript
  being written after the last event) and, when it was the project's last one, the project heading
- [x] Projects without live sessions get a "Remove" action; a removed project returns with the next session there
- [x] Tests: Markdown parser (4), removal of session + conversation + project (2 VM, 1 overview); suites and lint pass
- [x] Real test: removed "Agentmon Hooktest" from the phone; it disappeared

### Feature: Resume any saved conversation + remove from phone (M6, spec phase 6)
- [x] Checked the docs first (reported to the owner): `claude -p --resume <id>` uses the normal subscription login and
  plan limits (no API key; only `--bare` would need one), keeps the session id (`--fork-session` would not), and runs
  the project's hooks, so tracking, approvals and Stop keep working
- [x] Desktop `TranscriptStore`: lists a project's saved conversations (title: name → ai-title → first prompt),
  subfolders confirmed by `cwd`, case-insensitive on Windows
- [x] `HeadlessRunner` + `SessionResumer`: prompt via stdin, never the command line; resume only when no Claude has the
  conversation open; hooks aliased to the session the phone shows; phone-started runs hold permission prompts for
  the phone; Stop ends the run; failures shown as an error event
- [x] Phone: History per project (also for projects without live sessions), resume dialog, "Open now" for live
  conversations, ended sessions resume when you send a message, long-press **Remove from this phone** (stored with
  the computer's activity time, so clock differences cannot undo it)
- [x] Tests: transcripts (4), resume flow with a fake `claude` (5), overview hiding (2), Home remove (1), History VM (4);
  149 tests in total (app 62, shared 17, desktop 68, cli 2)
- [x] **Verified on the Xiaomi:** History listed 7 conversations with titles; resuming one answered from its earlier
  context; sending to the ended session resumed it, its Write prompt waited for the phone with Away mode off,
  Approve wrote the file; removing hid the session while its transcript stayed on the PC

### Feature: Alerts when the app is closed (M7, spec phase 7 option A)
- [x] Setting "Alerts when the app is closed" (off by default); POST_NOTIFICATIONS asked on Android 13+, with an
  explanation and a shortcut to the app's system page (battery, Xiaomi Autostart) if refused
- [x] `AgentMonitorService`: foreground service type `connectedDevice` (+ CHANGE_NETWORK_STATE) that keeps the shared
  connection open; quiet ongoing notification with connection status, "N sessions need you" and **Turn off**
- [x] `DetectSessionAlertsUseCase`: alert once when a session starts needing the user or errors, again only when the
  reason changes, cleared when answered; nothing replayed on start; skipped while the app is visible
- [x] Alerts contain only the project name and the reason; the lock screen shows "Claude needs you" only; tapping
  opens the session (`PendingNavigation`)
- [x] `BackgroundAlertsController` starts the service whenever the app is visible (Android forbids starting it from the
  background) and stops it when alerts are turned off or the phone is unpaired
- [x] Tests: alert detection (4), settings toggle (2); 133 tests in total (app 55, shared 14, desktop 59, cli 5)
- [x] **Verified on the Xiaomi (Android 12):** service in the foreground, alert "Claude wants your permission" while the
  app was in the background and again after swiping it out of recents, tap opened the session, Approve wrote the
  file, the alert cleared itself when the session was released

## 6. Screen ↔ Design Map
| Screen | Orientation | Design ref | Implementation path | Status |
|---|---|---|---|---|
| Home | portrait + resizable | spec §9.3 #2 | `presentation/home/HomeScreen.kt` | Done |
| Session detail (activity) | portrait + resizable | spec §9.3 #3 | `presentation/sessiondetail/SessionDetailScreen.kt` | Done (Activity + Terminal tabs, composer, controls) |
| Settings | portrait + resizable | spec §9.3 #6 | `presentation/settings/SettingsScreen.kt` | Done (notifications in M7) |
| Onboarding | portrait + resizable | spec §9.3 #1 | `presentation/onboarding/OnboardingScreen.kt` | Done |
| Pair (scan / code / confirm / wait / success) | portrait + resizable | spec §9.3 #1 | `presentation/pair/PairScreen.kt` | Done |
| Devices & Security | portrait + resizable | spec §9.3 #5 | `presentation/devicessecurity/DevicesSecurityScreen.kt` | Done |
| Desktop Pair / Approve / Devices / Activity | window | spec §9.4 | `desktop/.../ui/screens/` | Done |
| Desktop Overview | window | spec §9.4 | `desktop/.../ui/screens/OverviewScreen.kt` | Done |

## 7. Data Model & API
See `docs/protocol.md`. Domain models: `AgentSnapshotModel` (connection, computer, projects,
sessions, timelines), `SessionModel`, `TimelineEventModel`, `ConnectionStatus` (Connecting /
Connected / Reconnecting / Offline / Unauthorized(AuthProblem)), `PairingModel` (pinned computer, in its own
DataStore that is excluded from backups), and `AppSettingsModel` (theme, haptics).

## 8. Milestone Progress
| Milestone | Status |
|---|---|
| M0 Foundation | Done |
| M1 Local link (phase 1) | Done, gate verified on a real phone |
| M2 Tunnel (phase 2) | Done: tunnel `agentmon` as the Windows service; pairing and live sessions verified via the tunnel; mobile-data check needs the owner |
| M3 Pairing + mutual auth | Done, gate verified on a real phone |
| M4 Hooks + state machine | Done, verified with real `claude` sessions on this PC and the Xiaomi |
| M5a Control via hooks (Away mode) | Done, gate verified on a real phone |
| M5b Wrapper (`agentmon claude`) + terminal mirror | Done, gate verified on a real phone (plus Stop from the phone) |
| M6 Headless resume (History, resume, remove from phone) | Done, verified on a real phone |
| M7 Notifications (foreground service) | Done, verified on a real phone |
| M8 Hardening + QA | Partly done: stay awake, start with Windows, installed agent; installer/E2E/QA open |

## 9. Decisions & Assumptions
- **D1** `:shared` is plain Kotlin/JVM rather than KMP (both consumers are JVM).
- **D2** The Android module stays `:app` (the spec calls it `mobile/`); renaming would only add churn.
- **D3** The light theme uses darker status/brand variants for contrast (not in the spec).
- **D4** Spec phase 1 says "LAN", but §6 forbids non-loopback binding. The agent stays on 127.0.0.1 and phones
  use `adb reverse` in development.
- **D5** Added `session.history.result` to the protocol (the spec has the request but no reply).
- **D6** `ws://` is accepted only for 127.0.0.1/localhost; any other host must be `wss://`.
- **D7** Pairing data must never be backed up: `allowBackup=false` and extraction rules exclude everything.
- **D8** The device id is the first 32 hex characters of the SHA-256 of the phone's public key, so an id cannot be
  claimed without the matching private key.
- **D11** Pairing grants are least-privilege by default: read-only; "All projects" is on by default in the approve dialog.
- **D12** `/pair` holds the HTTP request for up to 90 s (under Cloudflare's 100 s origin timeout) instead of polling.
- **D13** The pairing QR also works as a pasteable code (`AGENTMON1:`…), for emulators and phones without a camera.
- **D15** Hooks use Claude Code's built-in `http` hook type instead of the spec's `agentmon-hook` helper: no executable, no
  Windows quoting, and a down agent never blocks Claude. SessionStart (command hooks only) is not used.
- **D16** `/hook` refuses anything carrying Cloudflare headers, since the tunnel also terminates on loopback.
- **D14** Refusals (revoked, not paired, auth failed, desktop key mismatch) stop reconnecting and clear the cached
  sessions, since nothing from that computer can be trusted until the phone is paired again.
- **D17** Remote control is built on hooks first (M5a, owner chose "Both"): it works for every hooked session with
  no wrapper. The PTY wrapper (M5b) adds typing into live interactive sessions and the terminal mirror.
- **D18** Away mode is off by default and after every agent restart, so Claude never waits on a phone the owner
  forgot about; turning it off releases anything held.
- **D19** Instructions are held/queued in memory only and never written to disk (spec: no prompt content in logs).
- **D20** The phone gets `terminal.screen` (emulated screen lines) instead of the spec's raw `terminal.chunk`:
  Claude Code redraws in place with cursor movement, so raw chunks cannot be shown as lines.
- **D21** A wrapper terminal is one phone session keyed by the wrapper id (hooks aliased via `X-Agentmon-Wrapper`),
  so it is controllable before the first prompt and stays one entry across `/clear`.
- **D22** Quick actions on wrapper sessions without a held hook press Claude's own keys (Enter/Esc) through a
  single `ClaudeCodePromptProfile`, so a Claude Code UI change is a one-file fix.
- **D23** Stop uses the strongest mechanism available per session: a held hook's own decision, Esc in a wrapper's
  terminal, otherwise `continue: false` on the next hook. Hooks cannot interrupt Claude between tool calls, so
  plain sessions stop at their next step, not instantly.
- **D24** The background service uses type `connectedDevice` (a network link to the user's own computer, with
  CHANGE_NETWORK_STATE): `dataSync` is limited to 6 h a day on Android 15, and `specialUse` needs Play review.
- **D25** Resume runs `claude -p --resume` with the prompt on stdin. Arguments never carry phone text, because
  `cmd.exe` (npm's `claude.cmd`) cannot quote arbitrary text safely.
- **D26** "Delete" is phone-only (owner's choice): hidden ids live in a local DataStore with the computer's activity
  time; transcripts on the computer are never touched.
- **D27** Resuming is refused while any Claude has the conversation open (two writers would interleave the transcript).
- **D28** (Superseded for releases by the JDK 24 jpackage app-images.) No new JDK was installed for packaging; the agent is "installed" as jars + `javaw` so it can start with
  Windows today. A real MSI needs `jpackage` (owner decision).
- **D29** Session topics are prompt text, so they travel like snippets (to granted phones only) but are left out of
  alerts, and are never stored on the computer beyond the in-memory session.
- **D9** Push notifications use option A, a foreground service (owner's choice).
- **D10** `AppRoot` (not `MainActivity`) applies the theme, because the theme mode comes from DataStore
  through `RootViewModel`.

## 10. Known Gaps / TODO
- Demo sessions are opt-in (`--demo`).
- Whether an already-running Claude session picks up newly installed hooks is not documented; the UI says to restart it.
- Away mode needs the phone to answer within 50 min; after that Claude falls back to its normal prompt.
- Without the wrapper, text typed on the phone reaches an interactive session only at its next Stop; start
  Claude with `agentmon claude` to type into it live.
- `agentmon claude` started from inside another Claude Code session inherits its session markers (Claude then
  says transcript saving is off); start it from a normal terminal.
- A `.cmd` given in `AGENTMON_CLAUDE` must not contain spaces (cmd.exe quoting); PATH lookups are fine.
- Plain (non-wrapper) sessions stop at their next tool call; a long answer with no tool call runs to its end.
- After an Esc stop, Claude restores the interrupted prompt into its input box; a phone message typed next is
  appended to it. Clearing the input first (e.g. Ctrl+U) is not verified yet.
- The terminal font lacks a few symbols Claude uses (e.g. `⏵⏵` in the status line), shown as boxes.
- Background alerts do not start by themselves after a phone reboot; opening the app once starts them again.
- Seen once: the phone's DNS returned a Cloudflare address (188.114.97.6) that this ISP could not reach, so the app
  showed Offline for a few minutes until DNS changed. Nothing in the app can fix that; it recovered on its own.
- History reads Claude Code's internal transcript format; a future Claude Code change may only degrade titles
  (sessions without a readable title are skipped).
- An interactive session closed without a SessionEnd hook (e.g. terminal window closed) stays IDLE in the agent and
  cannot be resumed from the phone until the agent forgets it (24 h) or restarts.
- Codex is not supported yet (spec: `CodexAdapter` later); everything here targets Claude Code.
- The desktop checks the tunnel before showing a pairing code and falls back to USB when it is unreachable.
- The desktop agent does not start with Windows yet (run `scripts\start-agent.cmd`); the tunnel service does. Packaging and auto-start are M8.
- Default phone name comes from the system (MIUI reports the model, e.g. "M2101K7AG"); it can be edited before sending.
- Not yet checked: API 24 device, tablet window, TalkBack pass (M8 QA).

## 11. How to Build & Run
See `CLAUDE.md` for the commands and `docs/setup.md` for step-by-step owner setup.
