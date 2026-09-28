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
- [x] Tests: state machine (7), installer (6), endpoint (5); 105 tests in total
- [x] **Gate:** a real `claude` session in a monitored folder showed on the Xiaomi with the correct states and
  timeline (Prompt → Read → Finished → Session ended)

## 6. Screen ↔ Design Map
| Screen | Orientation | Design ref | Implementation path | Status |
|---|---|---|---|---|
| Home | portrait + resizable | spec §9.3 #2 | `presentation/home/HomeScreen.kt` | Done |
| Session detail (activity) | portrait + resizable | spec §9.3 #3 | `presentation/sessiondetail/SessionDetailScreen.kt` | Done (composer/terminal in M5) |
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
| M5 Wrapper + control | Not started |
| M6 Headless resume | Not started |
| M7 Notifications (foreground service) | Not started |
| M8 Hardening + QA | Not started |

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
- **D9** Push notifications use option A, a foreground service (owner's choice).
- **D10** `AppRoot` (not `MainActivity`) applies the theme, because the theme mode comes from DataStore
  through `RootViewModel`.

## 10. Known Gaps / TODO
- Demo sessions are opt-in (`--demo`).
- Whether an already-running Claude session picks up newly installed hooks is not documented; the UI says to restart it.
- The composer, quick actions and terminal tab arrive in M5; they are not shown yet rather than stubbed.
- The desktop checks the tunnel before showing a pairing code and falls back to USB when it is unreachable.
- The desktop agent does not start with Windows yet (run `scripts\start-agent.cmd`); the tunnel service does. Packaging and auto-start are M8.
- Default phone name comes from the system (MIUI reports the model, e.g. "M2101K7AG"); it can be edited before sending.
- Not yet checked: API 24 device, tablet window, TalkBack pass (M8 QA).

## 11. How to Build & Run
See `CLAUDE.md` for the commands and `docs/setup.md` for step-by-step owner setup.
