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

## 6. Screen ↔ Design Map
| Screen | Orientation | Design ref | Implementation path | Status |
|---|---|---|---|---|
| Home | portrait + resizable | spec §9.3 #2 | `presentation/home/HomeScreen.kt` | Done |
| Session detail (activity) | portrait + resizable | spec §9.3 #3 | `presentation/sessiondetail/SessionDetailScreen.kt` | Done (composer/terminal in M5) |
| Settings | portrait + resizable | spec §9.3 #6 | `presentation/settings/SettingsScreen.kt` | Done (notifications in M7) |
| Onboarding + Pair | portrait | spec §9.3 #1 | – | M3 |
| Devices & Security | portrait | spec §9.3 #5 | – | M3 |
| Desktop Overview | window | spec §9.4 | `desktop/.../ui/screens/OverviewScreen.kt` | Done |

## 7. Data Model & API
See `docs/protocol.md`. Domain models: `AgentSnapshotModel` (connection, computer, projects,
sessions, timelines), `SessionModel`, `TimelineEventModel`, `ConnectionStatus` (Connecting /
Connected / Reconnecting / Offline), and `AppSettingsModel` (theme, server URL, haptics, device id in DataStore).

## 8. Milestone Progress
| Milestone | Status |
|---|---|
| M0 Foundation | Done |
| M1 Local link (phase 1) | Done, gate verified on a real phone |
| M2 Tunnel (phase 2) | **Waiting on owner**: docs/setup.md part C |
| M3 Pairing + mutual auth | Not started; independent of M2, next |
| M4 Hooks + state machine | Not started (blocked on M3; real data needs auth) |
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
- **D8** Before phase 3 the device id is a random UUID in DataStore; phase 3 replaces it with a Keystore key.
- **D9** Push notifications use option A, a foreground service (owner's choice).
- **D10** `AppRoot` (not `MainActivity`) applies the theme, because the theme mode comes from DataStore
  through `RootViewModel`.

## 10. Known Gaps / TODO
- No authentication until M3: don't run the tunnel with real sessions before then (docs/threat-model.md).
- Sessions are **demo data** (`DemoSessionSimulator`) until M4; run `--no-demo` once hooks exist.
- After a disconnect, the session status shown is the last known one; STALE detection comes in M4.
- The composer, quick actions and terminal tab arrive in M5; they are not shown yet rather than stubbed.
- The desktop sidebar has only Overview; Devices, Pair and Activity arrive in M3/M4.
- Not yet checked: API 24 device, tablet window, TalkBack pass (M8 QA).

## 11. How to Build & Run
See `CLAUDE.md` for the commands and `docs/setup.md` for step-by-step owner setup.
