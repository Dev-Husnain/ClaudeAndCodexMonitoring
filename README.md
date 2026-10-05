# AgentMon

**Watch and control Claude Code from your phone.** AgentMon shows every Claude Code session on your computer
live on your Android phone: what it is doing, when it needs you, and what it answered. You can approve
permission prompts, reply, stop Claude, type into its terminal and continue old conversations, from anywhere.

- **Android app.** Live status, alerts, a terminal view, replies rendered as Markdown, History and resume.
- **Desktop agent for Windows.** A tray app that receives Claude Code's hooks and talks to the phone. It only
  listens on `127.0.0.1`.
- **`agentmon` wrapper.** Start Claude with `agentmon claude` instead of `claude`, and the phone can see that
  terminal and type into it.

> Status: version 1.0.0, Windows + Android. Claude Code is supported; Codex is planned.

---

## Contents

1. [How it works](#how-it-works)
2. [What you need](#what-you-need)
3. [Install (release)](#install-release)
4. [Pair your phone](#pair-your-phone)
5. [Use it every day](#use-it-every-day)
6. [Reach your computer from anywhere (Cloudflare tunnel)](#reach-your-computer-from-anywhere-cloudflare-tunnel)
7. [Phone app guide](#phone-app-guide)
8. [Desktop agent guide](#desktop-agent-guide)
9. [Security and privacy](#security-and-privacy)
10. [Troubleshooting](#troubleshooting)
11. [Build from source](#build-from-source)
12. [Make a release](#make-a-release)
13. [Project layout and docs](#project-layout-and-docs)

---

## How it works

```
 Phone (AgentMon app)
        │  WebSocket, signed with a key that never leaves the phone
        ▼
 Cloudflare tunnel  ──or──  USB cable (adb reverse)
        │
        ▼
 Desktop agent (AgentMon.exe, tray)  listening on 127.0.0.1:8787 only
        ▲                      ▲
        │ hooks (HTTP)         │ terminal link (WebSocket)
 Claude Code  ◄──────────  agentmon claude  (wrapper around Claude's terminal)
```

1. When you add a project, the agent writes Claude Code **hooks** into that project's
   `.claude/settings.local.json`. Claude then reports every step to the agent: prompt sent, tool used,
   permission needed, finished, and so on.
2. If you start Claude with **`agentmon claude`**, the wrapper runs Claude in a pseudo-terminal and mirrors
   its screen to the agent. That lets the phone show the real terminal and type into it.
3. The agent never opens a port to your network. The phone reaches it through a **Cloudflare tunnel**, which
   forwards to `127.0.0.1:8787`, or through a **USB cable** with `adb reverse`.

There are three ways a session can be controlled:

| Started with | Phone can see | Phone can control |
|---|---|---|
| `agentmon claude` | Status, replies, live terminal | Type messages, keys (Enter, Esc, arrows…), Stop, approve prompts |
| `claude` (plain) | Status and replies | With **Away mode** on: approve or deny prompts, reply when Claude finishes, Stop |
| From the phone (History → resume) | Status and replies | Reply, approve prompts, Stop |

---

## What you need

- **Windows 10 or 11** (64-bit).
- **Claude Code**, installed and logged in: `claude` must work in a terminal.
- An **Android phone**, Android 7.0 (API 24) or newer.
- To use it away from home: a domain on **Cloudflare** (free plan) for the tunnel. At home, a USB cable is enough.

The release builds carry their own Java. Nothing else needs installing.

---

## Install (release)

Download from the [Releases](../../releases) page:

| File | What it is |
|---|---|
| `AgentMon-1.0.0-windows-x64.zip` | Desktop agent (`AgentMon\AgentMon.exe`) and the wrapper (`cli\agentmon.exe`) |
| `AgentMon-1.0.0-android.apk` | Phone app |

### On the computer

1. **Unzip** `AgentMon-1.0.0-windows-x64.zip` to a permanent place, for example `C:\Users\<you>\AgentMon`.
   Don't run it from the Downloads folder if you plan to turn on "Start with Windows".
2. **Start the agent:** double-click `AgentMon\AgentMon.exe`. A window opens and an icon appears in the tray.
   Closing the window keeps it running in the tray; use the tray menu to quit.
   Windows SmartScreen may warn about an unknown publisher (the app is not code-signed). Choose
   **More info → Run anyway**.
3. **Put the wrapper on your PATH**, so `agentmon` works in every terminal:
   - Press **Win**, type **environment**, and open **Edit environment variables for your account**.
   - Under **User variables**, select **Path**, then click **Edit…**, then **New**.
   - Paste the full path of the `cli` folder, for example `C:\Users\<you>\AgentMon\AgentMon-1.0.0-windows-x64\cli`.
   - Click **OK** on every window. **Restart** any open terminals and Android Studio.
   - Check it in a new terminal: `agentmon --version`.

   Or do it in PowerShell:
   ```powershell
   $dir = "C:\Users\<you>\AgentMon\AgentMon-1.0.0-windows-x64\cli"
   [Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path","User").TrimEnd(";") + ";" + $dir, "User")
   ```
4. Optional: on the agent's **Overview** tab, turn on **Start with Windows**, so the phone can reach your computer
   after a restart. **Stay awake while Claude works** is on by default; it keeps Windows from sleeping while a
   session runs.

### On the phone

1. Copy `AgentMon-1.0.0-android.apk` to the phone, or download it there, and open it.
2. Allow **Install unknown apps** for your browser or file manager when Android asks.
3. Open **AgentMon** and allow notifications.

---

## Pair your phone

Pairing happens once per phone. Only phones you approve on the computer can connect.

1. On the computer, in AgentMon, open **Devices** and click **Pair device**.
2. Pick how the phone will reach the computer:
   - **Anywhere · your-tunnel-host**: over the internet, through your Cloudflare tunnel. Until you set your
     address under **Overview → This computer → Phone access address**, it reads *not set up*. See
     [the tunnel section](#reach-your-computer-from-anywhere-cloudflare-tunnel).
   - **USB · adb reverse**: over a USB cable. Run `adb reverse tcp:8787 tcp:8787` after plugging in.
3. A QR code appears. On the phone, tap **Pair with your computer** and scan it. If the camera can't read it,
   paste the code shown under the QR instead (it starts with `AGENTMON1:`).
4. Check that the fingerprint matches on both screens, then **approve** on the computer. Choose:
   - **Which projects** the phone may see (all, or only some).
   - Whether it may **send input** (reply, approve, type, stop) or is **read-only**.

You can change or revoke a phone at any time in **Devices**. Revoking disconnects it at once.

---

## Use it every day

### Start Claude so the phone can control it

In any terminal (Windows Terminal, PowerShell, or the **Android Studio terminal**), go to your project and run:

```
agentmon claude
```

Anything after `claude` is passed through to Claude Code, for example:

```
agentmon claude --dangerously-skip-permissions
agentmon claude --resume
agentmon claude --model sonnet
```

The session appears on the phone right away under its project name. If the folder wasn't monitored yet,
AgentMon adds it by itself: it installs the hooks and adds `.claude/settings.local.json` to that project's
`.gitignore`. Your home folder, folders above it, and drive roots are never added. Phones that were limited
to chosen projects still need you to grant the new one in **Devices**.

**In Android Studio:** open the **Terminal** tool window and press **+** for a new tab. The tab opens in the
project folder; run `agentmon claude` there. If `agentmon` isn't found, Android Studio was started before you
changed PATH. Restart Android Studio, or refresh the tab once:

```powershell
$env:Path = [Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [Environment]::GetEnvironmentVariable("Path","User")
```

### Plain `claude` works too

Sessions started with plain `claude` in a monitored project still show on the phone, through the hooks. To
answer them from the phone, turn on **Away mode** (below).

### Away mode: when you leave your desk

Turn on **Away mode** on the phone or in the agent. While it is on:

- A **permission prompt** waits for the phone: approve or deny it there.
- When Claude **finishes**, it waits for your next instruction from the phone instead of going idle.
  Reply to keep it working, or tap **Let it stop**.
- The computer stays awake.

Turn Away mode off when you are back, and Claude behaves exactly as usual at the computer.

### Add a project by hand

In the agent, open **Projects → Add project** and pick the folder. This installs the hooks without starting
the wrapper. **Remove** uninstalls them.

---

## Reach your computer from anywhere (Cloudflare tunnel)

The agent only listens on `127.0.0.1`. To reach it over mobile data, run a Cloudflare tunnel that forwards a
hostname of yours to `http://localhost:8787`.

1. Add your domain to Cloudflare (free plan) and wait until it shows **Active**.
2. Install cloudflared: `winget install --id Cloudflare.cloudflared`
3. Run `cloudflared tunnel login` and pick your domain.
4. Run `cloudflared tunnel create agentmon` and note the tunnel ID.
5. Run `cloudflared tunnel route dns agentmon agent.<your-domain>`
6. Create `%USERPROFILE%\.cloudflared\config.yml`:
   ```yaml
   tunnel: <TUNNEL-ID>
   credentials-file: C:\Users\<you>\.cloudflared\<TUNNEL-ID>.json
   ingress:
     - hostname: agent.<your-domain>
       service: http://localhost:8787
     - service: http_status:404
   ```
7. Test it with `cloudflared tunnel run agentmon`, then open `https://agent.<your-domain>/health` on the phone
   over mobile data. It should show `{"status":"ok",...}`.
8. Run it as a service: `cloudflared service install` from an **Administrator** terminal. The full steps,
   including a Windows service gotcha, are in [docs/setup.md](docs/setup.md#c-cloudflare-tunnel-phase-2-remote-access-over-mobile-data).
9. **Tell AgentMon your address.** In the agent window, open **Overview**. Under **This computer → Phone access
   address**, type your hostname, for example `agent.<your-domain>`, and click **Save**. Only `https://`
   addresses are accepted; `https://` is added for you.
   - The address goes into every pairing QR code for the **Anywhere** route, and it is saved in
     `%APPDATA%\AgentMon\settings.properties`, so it survives restarts and updates.
   - Leave the field empty to pair over USB only.
   - Advanced: `AGENTMON_PUBLIC_URL` (environment variable) or `AgentMon.exe --public-url https://…` override the
     saved address. The field then shows where it comes from and can't be edited.
10. Pair the phone with the **Anywhere** option. If you already paired over USB, pair again with **Anywhere**:
    the address is part of the pairing.

Hooks and the wrapper link are refused when they arrive through the tunnel. They must come from the computer itself.

---

## Phone app guide

### Home

- **Connected / Offline** shows the link to your computer. When offline, the last known state stays visible
  with a "last seen" note.
- **Away mode** switch.
- **Needs you**: sessions waiting for a permission or your answer, pinned at the top.
- **Projects**: one section per project, newest activity first. Each card shows the project name, the
  conversation topic (Claude's title or your first message), the status, the last reply and the last tool.
- **History** next to a project lists its saved conversations.
- **Remove**: long-press an idle or ended session to remove it from the phone. Its conversation also leaves
  History, and the project heading goes when it was the last one. A project without live sessions has a
  **Remove** button. Nothing is deleted on the computer, and it all comes back when Claude works there again.

### Session screen

- **Header**: status, when it started, how it is controlled, and Claude's latest reply rendered as
  **Markdown** (headings, lists, code blocks, tables, links). You can select and copy the text.
- **Activity tab**: a timeline of prompts, tools, notifications and answers.
- **Terminal tab** (sessions started with `agentmon claude`): the live terminal, with colours. The key row sends
  keys Claude's screen needs:

  | Key | Use it to |
  |---|---|
  | **Enter** | Confirm a choice or send a typed line |
  | **Esc** | Interrupt Claude or close a menu |
  | **Tab** / **⇧ Tab** | Autocomplete / switch Claude's mode (plan, auto-accept…) |
  | **↑ ↓** | Move through menus and options |
  | **1 2 3** | Pick a numbered option in a prompt |
  | **Ctrl+C** | Cancel |

- **Message box**: multi-line. Enter adds a new line; the send button sends. For a wrapper session the text is
  typed into Claude's terminal; otherwise it is the answer to a held prompt, or it resumes an ended
  conversation.
- **Stop Claude**: interrupts the current work. For `agentmon claude` sessions it presses Esc. For others it
  stops Claude at its next step.

### History and resume

**History** shows a project's saved conversations, newest first, with their titles. Tap one and type a message.
The computer continues that conversation in the background (`claude -p --resume`, with your normal Claude login)
and it appears as a live session. Permission prompts in it wait for the phone. A conversation that is open in a
terminal can't be resumed twice.

### Alerts

With **Settings → Alerts when the app is closed** on, the app keeps a small connection alive (a silent notification) and
alerts you when a session needs you, finishes, or fails, even with the app closed. Alerts show only the project
name; your prompts never appear on the lock screen. For reliable alerts, allow the app to run without battery
restrictions (**Settings → Battery & background**).

### Settings

Theme (system, light, dark), background alerts, battery optimisation, the paired computer, and unpairing.

---

## Desktop agent guide

| Tab | What it does |
|---|---|
| **Overview** | Agent status, connected phones, live sessions, and **This computer**: *Phone access address* (your tunnel hostname), *Stay awake while Claude works*, *Start with Windows* |
| **Projects** | Monitored folders: add or remove, reinstall hooks, see when the last hook arrived |
| **Devices** | **Pair device**, paired phones, their project access and input permission, **Revoke** |
| **Activity** | Audit log: pairings, connections, refusals, project changes (never prompt content) |

Command-line options for `AgentMon.exe`:

| Option | Meaning |
|---|---|
| `--background` | Start in the tray without opening the window (used by Start with Windows) |
| `--public-url https://…` | Overrides the *Phone access address* from Overview (so does `AGENTMON_PUBLIC_URL`) |
| `--demo` | Add fake sessions, to try the phone without Claude Code |

The agent keeps its keys, database and settings in `%APPDATA%\AgentMon`, not in the program folder, so updating
the program keeps your pairings.

---

## Security and privacy

- **Loopback only.** The agent binds to `127.0.0.1:8787`, never to your network.
- **Paired phones only.** Each phone has a P-256 key created in Android's Keystore that can't be exported. Every
  connection answers a fresh challenge signed with it. The phone pins the computer's key from the QR code.
- **Approval on the computer.** A phone gets only the projects and the input permission you grant, and you can
  revoke it at once.
- **Pairing codes** expire after 2 minutes and work once. Failed attempts are rate-limited.
- **Hooks** need a shared secret that lives only in the gitignored `.claude/settings.local.json`, and are refused
  through the tunnel.
- **Nothing sensitive on disk or in alerts.** Prompts, replies and terminal output are never logged to disk by
  AgentMon, and notifications show only the project name.
- **Local removal.** "Remove" on the phone only hides things on that phone.

More in [docs/threat-model.md](docs/threat-model.md).

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `agentmon` is not recognized | The `cli` folder is not on PATH, or the terminal or Android Studio started before you changed it. Restart it, or refresh PATH as shown above. |
| PowerShell says "Unexpected token 'claude'" | You ran a quoted path without `&`. Use `& "C:\path\agentmon.exe" claude`, or put it on PATH. |
| Session doesn't appear on the phone | Check the agent is running (tray icon) and the phone shows **Connected**. With plain `claude`, it appears after the first prompt. A phone limited to some projects needs the project granted in **Devices**. |
| Pairing only offers USB / "Anywhere · not set up" | Enter your tunnel hostname under **Overview → This computer → Phone access address** and click **Save**. |
| Phone shows **Offline** | Open `https://agent.<your-domain>/health` on the phone. If that fails, check the cloudflared service. Over USB, run `adb reverse tcp:8787 tcp:8787` again after reconnecting. |
| "Start with Windows" can't be turned on | Run the agent from `AgentMon.exe` in a permanent folder, not from a build or the Downloads folder. |
| Alerts don't arrive with the app closed | Turn on **Alerts when the app is closed** and remove battery restrictions for AgentMon. Some phones (Xiaomi, Oppo…) also need "Autostart" allowed. |
| Port 8787 already in use | Another copy of the agent is running. Quit it from the tray, or find it with `netstat -ano \| findstr :8787`. |

---

## Build from source

Requirements: Android Studio (its bundled JBR is used for Gradle), Git, and Claude Code.

```bash
# Git Bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

./gradlew :app:assembleDebug        # Android APK
./gradlew :app:installDebug         # install on a connected phone
./gradlew :desktop:run              # desktop agent (add --args="--demo" for fake sessions)
./gradlew :cli:installDist          # wrapper -> cli/build/install/agentmon/bin/agentmon.bat
./gradlew :desktop:installAgent     # agent without Gradle -> %LOCALAPPDATA%\AgentMon\agent (quit it first)

./gradlew :app:testDebugUnitTest :shared:test :desktop:test :cli:test   # all tests
./gradlew :app:lintDebug            # must report "No issues found"
```

The project uses Kotlin 2.2.10. Library versions are pinned to releases built for Kotlin 2.2 or older; check a
library's Kotlin requirement before updating it.

---

## Make a release

The Windows package needs a JDK 17+ that includes `jpackage`. Android Studio's JBR does not have it, so install
a JDK first, for example `winget install EclipseAdoptium.Temurin.21.JDK`.

```powershell
$env:JPACKAGE_JDK = "C:\Program Files\Java\jdk-24"   # your JDK with jpackage
.\scripts\package-release.ps1 -Version 1.0.0
```

This writes to `dist\`:

- `AgentMon-1.0.0-windows-x64.zip`: `AgentMon\AgentMon.exe` and `cli\agentmon.exe`, each with a trimmed Java runtime.
- `AgentMon-1.0.0-android.apk`: the phone app. It is debug-signed. For a store release, create your own signing
  key and keep it out of git; `*.jks` and `*.keystore` are ignored.

Then upload both files to a new GitHub release, either through **Releases → Draft a new release** on GitHub, or:

```powershell
gh release create v1.0.0 dist\AgentMon-1.0.0-windows-x64.zip dist\AgentMon-1.0.0-android.apk --title "AgentMon 1.0.0" --notes "First release"
```

---

## Project layout and docs

| Module | What it is |
|---|---|
| `shared/` | Kotlin/JVM: the wire protocol (messages, DTOs, pairing, crypto helpers), used by both apps |
| `app/` | Android app: Jetpack Compose, Koin, Navigation 3, Ktor client, DataStore |
| `desktop/` | Compose Desktop tray app plus the Ktor server on `127.0.0.1:8787`, SQLDelight, hooks, the state machine |
| `cli/` | `agentmon claude`: the pseudo-terminal wrapper (pty4j / ConPTY) |

| Document | Contents |
|---|---|
| [docs/setup.md](docs/setup.md) | Step-by-step setup, including the Cloudflare service details |
| [docs/protocol.md](docs/protocol.md) | The phone ↔ computer protocol |
| [docs/threat-model.md](docs/threat-model.md) | Security model |
| [PROJECT-GUIDE.md](PROJECT-GUIDE.md) | Architecture, decisions and milestone progress |
| [AGENT_MONITOR_SPEC.md](AGENT_MONITOR_SPEC.md) | The product specification |
