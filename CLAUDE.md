# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Read first

- `AGENT_MONITOR_SPEC.md`: the product spec for **AgentMon** and the single source of truth for *what* to build. It covers the desktop agent, mobile app, WebSocket protocol, security model, UI brief and build phases 0–8.
- `CLAUDE-CODE-GUIDELINES.md`: the permanent engineering and workflow rules (architecture, naming, DI, Compose conventions, milestones, git, definition of done). Follow it for *how* to build. Do not copy its rules into this file. It says it must not be edited during a build, and it names `PROJECT-GUIDE.md` as the progress record the agent maintains.

**Known conflict:** the "Project inputs" block in `CLAUDE-CODE-GUIDELINES.md` was left over from another project ("JK Media Downloader", `com.media.downloader`). For this repo, take app identity, scope and requirements from `AGENT_MONITOR_SPEC.md`. Raise the conflict with the owner instead of acting on those inputs.

## Current state

This is a fresh Android Studio template. It has a single `:app` module (package `com.claude.codex.ai.monitoring`) containing only `MainActivity` and a Compose theme. Nothing from the spec has been built yet, and git has not been initialized.

The spec's target layout is a Gradle multi-module build: `shared/` (KMP protocol models, envelope and crypto helpers), `desktop/` (Compose Desktop, Ktor server on `127.0.0.1:8787`, hook receiver, pty4j injector) and `mobile/` (the Android app). Spec §14 says to propose the exact module structure and get the owner's confirmation before generating code at scale.

## Build and test

The toolchain is Gradle 9.6 (wrapper), AGP 9.4.1, Kotlin 2.2.10 and Compose BOM 2026.02.01. `compileSdk`/`targetSdk` are 37 and `minSdk` is 24. Java source/target is 11. Dependencies and plugins go through `gradle/libs.versions.toml`, and the configuration cache is on. On Windows, use `gradlew.bat` or `./gradlew` from Git Bash.

```
./gradlew assembleDebug                      # build debug APK
./gradlew installDebug                       # install on a connected device/emulator
./gradlew testDebugUnitTest                  # JVM unit tests
./gradlew testDebugUnitTest --tests "com.claude.codex.ai.monitoring.ExampleUnitTest"   # single test class (append .method for one test)
./gradlew connectedDebugAndroidTest          # instrumented tests (device required)
./gradlew lint                               # Android lint
```

R8 keep rules are in `app/src/main/keepRules/` (the AGP 9 location), not in `proguard-rules.pro`. Release optimization is currently disabled.

## Non-negotiables from the spec (easy to violate by accident)

- Never bind the desktop agent to a public interface (only `127.0.0.1`), and never weaken pairing or mutual auth "to make it work" (spec §6, §14.6).
- Before implementing hooks, check exact Claude Code hook event names and payloads against the current official docs. Also verify headless `-p` billing and auth against the docs. The spec says not to trust its own payload shapes.
- Put Claude Code UI-dependent keystrokes and prompt detection behind `PromptProfile`, and agent-specific logic behind `AgentAdapter`.
- When a step needs the owner (Cloudflare tunnel/DNS, Firebase, installing tools), stop and tell them exactly what to do (spec §3).
