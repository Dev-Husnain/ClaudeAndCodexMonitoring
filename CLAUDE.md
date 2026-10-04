# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Read first

- `CLAUDE-CODE-GUIDELINES.md` holds the permanent engineering and workflow rules plus the Project Inputs. Follow it; don't copy its rules here.
- `AGENT_MONITOR_SPEC.md` is the product spec: what to build, the security model and phases 0–8.
- `PROJECT-GUIDE.md` is the living record of architecture, milestone progress, decisions and known gaps. Resume work from its §8, and update it in the same commit as the work.
- `docs/` contains `setup.md` (owner setup steps), `protocol.md` and `threat-model.md`. `design/tokens.md` is the design snapshot the theme files are generated from.

## Modules

- `:shared` is Kotlin/JVM with the wire protocol (`Message`, `ProtocolCodec`, DTOs). It is used by both apps.
- `:app` is the Android app (Compose, Koin, Navigation 3, Ktor client, DataStore).
- `:desktop` is the Compose Desktop tray app plus the Ktor server, bound to `127.0.0.1:8787` only.
- `:cli` is `agentmon claude`, the PTY wrapper that lets the phone see and type into Claude's terminal.

## Build and test

Use the Android Studio JBR: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (Git Bash) before running `./gradlew`.

```
./gradlew :app:assembleDebug                 # Android APK
./gradlew :app:installDebug                  # install on device/emulator
./gradlew :desktop:run                       # desktop agent (real sessions from Projects; --args="--demo" adds fake ones)
./gradlew :cli:installDist                   # agentmon wrapper -> cli/build/install/agentmon/bin
./gradlew :desktop:installAgent              # agent without Gradle -> %LOCALAPPDATA%\AgentMon\agent (quit it first)
./gradlew :app:testDebugUnitTest :shared:test :desktop:test :cli:test   # all unit/integration tests
./gradlew :app:testDebugUnitTest --tests "com.claude.codex.ai.monitoring.data.repo.AgentStateReducerTest"   # one class
./gradlew :app:lintDebug                     # must report "No issues found"
adb reverse tcp:8787 tcp:8787                # lets the phone reach the laptop agent over USB
```

## Gotchas

- Library versions are pinned to releases built against Kotlin ≤ 2.2 (the project is on Kotlin 2.2.10). Check a library's Kotlin stdlib requirement before bumping it. Lint's "newer version" checks are disabled on purpose in `app/lint.xml`.
- ViewModels run an endless relative-time ticker. In tests, give `Dispatchers.Main` its own `StandardTestDispatcher` and call `scheduler.runCurrent()`, because `runTest` draining it would hang (see `HomeViewModelTest`).
- R8 keep rules live in `app/src/main/keepRules/` (AGP 9), not in `proguard-rules.pro`.
- The Bash tool can fail on apostrophes inside heredocs; write source files with the Write tool.
- `:desktop:run` runs from `build/classes`: rebuilding the desktop while it runs swaps classes under it
  (ClassNotFoundException, HTTP 500 on /hook). Restart the agent after every desktop build.
- Stopping `./gradlew :desktop:run` can leave the agent's JVM running on port 8787, and a new run then fails or
  the phone keeps talking to old code. Check with `netstat -ano | grep ":8787 .*LISTEN"` and kill that PID.
