# AgentMon

**Watch and control Claude Code from your phone.** AgentMon shows every Claude Code session on your computer
live on your Android phone: what it is doing, when it needs you, and what it answered. You can approve
permission prompts, reply, stop Claude, type into its terminal and continue old conversations, from anywhere.

- **Android app.** Live status, alerts, a terminal view, replies rendered as Markdown, History and resume.
- **Desktop agent for Windows.** A tray app that receives Claude Code's hooks and talks to the phone. It only
  listens on `127.0.0.1`.
- **`agentmon` wrapper.** Start Claude with `agentmon claude` instead of `claude`, and the phone can see that
  terminal and type into it.

> Status: version 1.0.4, Windows + Android. Claude Code is supported; Codex is planned.

---

## Contents

1. [Quick start](#quick-start)
2. [How it works](#how-it-works)
3. [What you need](#what-you-need)
4. [Step 1: Install on the computer](#step-1-install-on-the-computer)
5. [Step 2: Install the phone app](#step-2-install-the-phone-app)
6. [Step 3: Choose how the phone reaches the computer](#step-3-choose-how-the-phone-reaches-the-computer)
   - [Option A: USB cable](#option-a-usb-cable)
   - [Option B: your own tunnel (use it anywhere)](#option-b-your-own-tunnel-use-it-anywhere)
7. [Step 4: Pair your phone](#step-4-pair-your-phone)
8. [Step 5: Start Claude through AgentMon](#step-5-start-claude-through-agentmon)
9. [Running the agent](#running-the-agent)
10. [Everyday use](#everyday-use)
11. [Phone app guide](#phone-app-guide)
12. [Desktop agent guide](#desktop-agent-guide)
13. [Security and privacy](#security-and-privacy)
14. [Troubleshooting](#troubleshooting)
15. [Build from source](#build-from-source)
16. [Make a release](#make-a-release)
17. [Project layout and docs](#project-layout-and-docs)

---

## Quick start

1. Download `AgentMon-1.0.4-windows-x64.zip` and `AgentMon-1.0.4-android.apk` from [Releases](../../releases).
2. **Computer:** unzip to a permanent folder and start `AgentMon\AgentMon.exe`, then add the `cli` folder to PATH.
   ([Step 1](#step-1-install-on-the-computer))
3. **Phone:** install the APK. ([Step 2](#step-2-install-the-phone-app))
4. **Connection:** for use anywhere, set up a tunnel and enter its address under **Overview → This computer → Phone
   access address**. To try it on your desk first, use a USB cable.
   ([Step 3](#step-3-choose-how-the-phone-reaches-the-computer))
5. **Pair:** in AgentMon click **Pair device** and scan the QR code with the app. ([Step 4](#step-4-pair-your-phone))
6. **Use:** in your project folder run `agentmon claude`. The session appears on the phone.
   ([Step 5](#step-5-start-claude-through-agentmon))

---

## How it works

```
 Phone (AgentMon app)
        │  encrypted WebSocket; the phone proves who it is with a key that never leaves it
        ▼
 your tunnel (e.g. Cloudflare)  ──or──  USB cable (adb reverse)
        │
        ▼
 Desktop agent (AgentMon.exe, in the tray)  listening on 127.0.0.1:8787 only
        ▲                      ▲
        │ hooks (HTTP)         │ terminal link (WebSocket)
 Claude Code  ◄──────────  agentmon claude  (runs Claude and mirrors its terminal)
```

1. **Hooks.** For every project it watches, the agent writes Claude Code hooks into that project's
   `.claude/settings.local.json`. Claude then reports each step to the agent: prompt sent, tool used,
   permission needed, finished, and so on.
2. **The wrapper.** When you start Claude with **`agentmon claude`**, the wrapper runs Claude in a pseudo-terminal
   and mirrors its screen to the agent. The phone can then show the real terminal and type into it. In your own
   terminal, Claude looks and works exactly as usual.
3. **The connection.** The agent never opens a port to your network. The phone reaches it through **your tunnel**,
   which forwards an `https://` address to `127.0.0.1:8787`, or through a **USB cable** with `adb reverse`.

What the phone can do depends on how Claude was started:

| Started with | Phone can see | Phone can control |
|---|---|---|
| `agentmon claude` | Status, replies, live terminal | Type messages, keys (Enter, Esc, arrows…), Stop, approve prompts |
| `claude` (plain) | Status and replies | With **Away mode** on: approve or deny prompts, reply when Claude finishes, Stop |
| From the phone (History → resume) | Status and replies | Reply, approve prompts, Stop |

---

## What you need

- **Windows 10 or 11**, 64-bit.
- **Claude Code**, installed and logged in: `claude` must work in a terminal.
- An **Android phone** with Android 7.0 or newer.
- One way for the phone to reach the computer:
  - **for use anywhere:** a tunnel, for example a free Cloudflare Tunnel on a domain you own ([Option B](#option-b-your-own-tunnel-use-it-anywhere));
  - **for trying it on your desk:** a USB cable and Android's platform tools ([Option A](#option-a-usb-cable)).

The Windows download carries its own Java. Nothing else needs installing for AgentMon itself.

---

## Step 1: Install on the computer

Download from the [Releases](../../releases) page:

| File | What it is |
|---|---|
| `AgentMon-1.0.4-windows-x64.zip` | Desktop agent (`AgentMon\AgentMon.exe`) and the wrapper (`cli\agentmon.exe`) |
| `AgentMon-1.0.4-android.apk` | Phone app |

1. **Unzip** `AgentMon-1.0.4-windows-x64.zip` to a folder you will keep, for example `C:\Users\<you>\AgentMon`.
   You get:
   ```
   AgentMon-1.0.4-windows-x64\
     AgentMon\AgentMon.exe     the desktop agent
     cli\agentmon.exe          the wrapper you run instead of "claude"
     README.txt
   ```
   Don't run it from the Downloads folder if you plan to turn on "Start with Windows".
2. **Start the agent:** double-click `AgentMon\AgentMon.exe`. A window opens and an AgentMon icon appears in the
   tray, next to the clock.
   - Windows SmartScreen may warn about an unknown publisher, because the app is not code-signed. Click
     **More info → Run anyway**.
   - If Windows Firewall asks about network access, you can choose **Cancel**. The agent only talks to this computer.
3. **Put the wrapper on your PATH**, so `agentmon` works in every terminal:
   - Press **Win**, type **environment**, and open **Edit environment variables for your account**.
   - Under **User variables**, select **Path**, click **Edit…**, then **New**.
   - Paste the full path of the `cli` folder, for example
     `C:\Users\<you>\AgentMon\AgentMon-1.0.4-windows-x64\cli`.
   - Click **OK** on every window.
   - **Close and reopen** your terminals. In Android Studio, close and reopen the IDE.
   - Check it in a new terminal: `agentmon --version` should print `agentmon 1.0.4`.

   Or do the same in PowerShell:
   ```powershell
   $dir = "C:\Users\<you>\AgentMon\AgentMon-1.0.4-windows-x64\cli"
   [Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path","User").TrimEnd(";") + ";" + $dir, "User")
   ```
4. **Optional:** on the agent's **Overview** tab, turn on **Start with Windows**, so the phone can reach your
   computer after a restart. **Stay awake while Claude works** is on by default: Windows won't go to sleep while a
   session runs.

---

## Step 2: Install the phone app

1. Get `AgentMon-1.0.4-android.apk` onto the phone: open the Releases page in the phone's browser and download
   it, or copy it over by cable or cloud drive.
2. Tap the file. When Android asks, allow **Install unknown apps** for your browser or file manager, then tap
   **Install**. Play Protect may warn that the app is unknown; choose **Install anyway**.
3. Open **AgentMon** and allow notifications. They tell you when Claude needs you.

---

## Step 3: Choose how the phone reaches the computer

| | Option A: USB cable | Option B: your own tunnel |
|---|---|---|
| Works | Only while the phone is plugged into the computer | Anywhere: mobile data, any Wi-Fi |
| You need | USB debugging and Android's platform tools | A tunnel, e.g. a domain on Cloudflare (free plan) |
| Setup time | 5 minutes | 20–30 minutes, once |
| Best for | Trying AgentMon out | Daily use, being away from your desk |

You can start with USB and add a tunnel later. Phones are then paired again with **Anywhere**.

### Option A: USB cable

The phone talks to the agent through the cable using `adb reverse`.

1. **Turn on USB debugging** on the phone:
   - **Settings → About phone**, tap **Build number** seven times until it says you are a developer. On Xiaomi,
     tap **MIUI version** instead.
   - **Settings → System → Developer options**, turn on **USB debugging**.
2. **Install Android's platform tools** on the computer. They include `adb`:
   ```powershell
   winget install Google.PlatformTools
   ```
   Open a new terminal afterwards. If you have Android Studio, `adb` is already in
   `%LOCALAPPDATA%\Android\Sdk\platform-tools`.
3. **Connect the phone** by USB. On the phone, accept **Allow USB debugging?** Then check with `adb devices`; the
   phone should be listed as `device`.
4. **Forward the agent's port** to the phone:
   ```powershell
   adb reverse tcp:8787 tcp:8787
   ```
   Run this again **every time** you plug the phone back in or restart the computer.
5. Continue with [Step 4](#step-4-pair-your-phone) and choose **USB · adb reverse**.

### Option B: your own tunnel (use it anywhere)

A tunnel gives your computer a public `https://` address, for example `https://agent.example.com`, without
opening any port. The tunnel program on your computer keeps an outgoing connection to the tunnel service, and
passes the phone's requests to the agent at `http://localhost:8787`. You don't need router settings or a fixed IP.

**AgentMon itself needs only one setting: your address.** Everything else is setting up the tunnel.

**The full step-by-step guide is [docs/tunnel.md](docs/tunnel.md).** It covers Cloudflare from start to finish,
including running it as a Windows service, ngrok and Tailscale Funnel, and troubleshooting. In short, with
Cloudflare:

1. **Get a domain onto Cloudflare** (free plan): add the domain at dash.cloudflare.com, change its nameservers at
   your registrar to the two Cloudflare gives you, and wait until it shows **Active**.
2. **Install cloudflared** with `winget install --id Cloudflare.cloudflared`, then in a new terminal:
   ```powershell
   cloudflared tunnel login                                   # pick your domain in the browser
   cloudflared tunnel create agentmon                         # note the tunnel ID it prints
   cloudflared tunnel route dns agentmon agent.example.com    # your chosen hostname
   ```
3. **Write `%USERPROFILE%\.cloudflared\config.yml`:**
   ```yaml
   tunnel: <TUNNEL-ID>
   credentials-file: C:\Users\<you>\.cloudflared\<TUNNEL-ID>.json
   ingress:
     - hostname: agent.example.com
       service: http://localhost:8787
     - service: http_status:404
   ```
4. **Test it:** with AgentMon running, start `cloudflared tunnel run agentmon`. Turn off Wi-Fi on the phone and open
   `https://agent.example.com/health` in its browser. You should see `{"status":"ok",...}`.
5. **Make it permanent:** install the tunnel as a Windows service so it starts with the computer
   ([docs/tunnel.md, step 7](docs/tunnel.md#7-run-the-tunnel-as-a-windows-service)).
6. **Tell AgentMon your address:** in the AgentMon window, go to **Overview → This computer → Phone access
   address**, type `agent.example.com` and click **Save**.
   - Only `https` hostnames are accepted, and `https://` is added for you.
   - The address is saved in `%APPDATA%\AgentMon\settings.properties` and kept across restarts and updates.
   - Leave the field empty to pair over USB only.
   - Advanced: `AGENTMON_PUBLIC_URL`, or `AgentMon.exe --public-url https://…`, overrides the field.
7. Continue with [Step 4](#step-4-pair-your-phone) and choose **Anywhere**.

Rules for any tunnel:

- The address must be `https://`, because the phone refuses plain `http://`.
- The tunnel must forward to `http://localhost:8787`, pass WebSockets through, and keep the same address. If the
  address changes, every phone must pair again.
- No login page may sit in front of the address, such as Cloudflare Access. The app can't sign in through a
  browser page, and AgentMon already only admits paired phones.

Claude's hooks and the `agentmon claude` terminal link are refused when they arrive through any tunnel. They must
come from the computer itself.

---

## Step 4: Pair your phone

Pairing happens once per phone. Only phones you approve on the computer can connect.

1. On the computer, click **Pair device** (bottom left in AgentMon, or **Devices → Pair device**).
2. Check the route at the top of the dialog:
   - **Anywhere · agent.example.com** is used whenever a Phone access address is saved. It reads *not set up*
     while there is none. If this computer can't reach the address at that moment, a red note says so. Pairing
     still works as long as `https://<your address>/health` opens in the phone's browser.
   - **USB · adb reverse** is for phones on a cable. Make sure `adb reverse tcp:8787 tcp:8787` was run. A phone
     without adb can never pair with a USB code.
3. On the phone, open AgentMon, tap **Pair with your computer** and scan the QR code. If the camera can't read it,
   copy the code shown under the QR (it starts with `AGENTMON1:`) to the phone and paste it.
4. The phone shows the computer's fingerprint, and the computer shows the phone's. Check they match.
5. On the computer, **approve** the phone and choose:
   - **which projects** it may see: all, or only some;
   - whether it may **send input** (reply, approve, type, stop) or is **read-only**.
6. The phone opens its Home screen and shows **Connected**.

The QR code is valid for 2 minutes and works once; click **New code** if it expires. You can change a phone's
access or **revoke** it at any time in **Devices**. Revoking disconnects it at once.

---

## Step 5: Start Claude through AgentMon

In any terminal (Windows Terminal, PowerShell, or the **Android Studio terminal**), go to your project and run:

```
agentmon claude
```

instead of `claude`. Anything after `claude` is passed on to Claude Code:

```
agentmon claude --dangerously-skip-permissions
agentmon claude --resume
agentmon claude --model sonnet
```

What happens:

- Claude starts in your terminal exactly as usual, and you can keep working there.
- The session appears on the phone right away, under the project's name, for example "ShopKart". The name comes
  from Android Studio, the Gradle or package file, or the folder name.
- The first time you run it in a folder, AgentMon starts watching that folder: it installs the hooks and adds
  `.claude/settings.local.json` to the project's `.gitignore`. Your home folder, folders above it, and drive roots
  are never added. Phones limited to chosen projects need the new project granted in **Devices**.
- On the phone, open the session to see Claude's latest reply, the activity, and the live **Terminal**. Type a
  message at the bottom and it is typed into Claude's terminal on the computer.

**In Android Studio:** open the **Terminal** tool window (Alt+F12) and press **+** for a new tab. It opens in the
project folder; run `agentmon claude` there. If `agentmon` isn't found, Android Studio was started before PATH was
changed. Restart Android Studio, or refresh the tab once:

```powershell
$env:Path = [Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [Environment]::GetEnvironmentVariable("Path","User")
```

In PowerShell, a quoted path needs `&` in front: `& "C:\…\cli\agentmon.exe" claude`. With the `cli` folder on
PATH, just type `agentmon claude`.

---

## Running the agent

- **Start:** `AgentMon\AgentMon.exe`. With **Start with Windows** on, it starts by itself in the tray when you sign in.
- **Open the window:** double-click the tray icon. **Closing the window** keeps the agent running in the tray.
- **Quit:** right-click the tray icon → **Quit**. Phones then show the computer as offline, and Claude keeps
  working normally.
- **Is it running?** The tray icon is there, and `http://127.0.0.1:8787/health` in a browser on the computer shows
  `{"status":"ok",...}`.
- **Update:** quit the agent, unzip the new release over the old folder (or into a new one, and update PATH and
  Start with Windows), then start it again. Pairings, projects and settings live in `%APPDATA%\AgentMon` and are kept.
  On the phone, install the new APK over the old app.
- **Uninstall:**
  1. In the agent, **Projects → Remove** each project, which removes its hooks.
  2. Turn off **Start with Windows** and quit.
  3. Delete the program folder and `%APPDATA%\AgentMon`, and remove the `cli` folder from PATH.
  4. Uninstall the app on the phone.

---

## Everyday use

### Plain `claude` works too

Sessions started with plain `claude` in a watched project still show on the phone, through the hooks. To answer
them from the phone, turn on **Away mode**.

### Away mode: when you leave your desk

Turn on **Away mode** on the phone or in the agent. While it is on:

- A **permission prompt** waits for the phone: approve or deny it there.
- When Claude **finishes**, it waits for your next instruction from the phone instead of going idle. Reply to keep
  it working, or tap **Let it stop**.
- The computer stays awake.

Turn Away mode off when you are back, and Claude behaves exactly as usual at the computer.

### Continue an old conversation

On the phone, tap **History** next to a project, pick a conversation and type a message. The computer continues it
in the background with your normal Claude login.

### Add or remove a project by hand

In the agent, **Projects → Add project** watches a folder without starting the wrapper. **Remove** stops watching it
and removes its hooks.

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
| **Activity** | Audit log: pairings, connections, refusals, project changes (never prompt content). **Clear activity** empties it after a confirmation; one entry records that it was cleared |

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
| Phone says "Pairing did not complete: could not reach your computer" | The QR code was for USB. Click **Pair device** again and check it says **Anywhere**. Open `https://<your address>/health` in the phone's browser; if that fails, the tunnel or the phone's network (VPN, DNS) is the problem |
| Pairing dialog warns that this computer can't reach the address | Usually this computer's DNS. Pairing still works if the phone can open `/health`. To clear it, set the computer's DNS to `1.1.1.1` (Settings → Network → your connection → DNS server assignment → Manual) |
| Phone is connected but has no keyboard or terminal keys | It was approved **read-only**. On the computer, **Devices → Can send input** |
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
.\scripts\package-release.ps1 -Version 1.0.4
```

This writes to `dist\`:

- `AgentMon-1.0.4-windows-x64.zip`: `AgentMon\AgentMon.exe` and `cli\agentmon.exe`, each with a trimmed Java runtime.
- `AgentMon-1.0.4-android.apk`: the phone app. It is debug-signed. For a store release, create your own signing
  key and keep it out of git; `*.jks` and `*.keystore` are ignored.

Then upload both files to a new GitHub release, either through **Releases → Draft a new release** on GitHub, or:

```powershell
gh release create v1.0.4 dist\AgentMon-1.0.4-windows-x64.zip dist\AgentMon-1.0.4-android.apk --title "AgentMon 1.0.4" --notes "First release"
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
| [docs/tunnel.md](docs/tunnel.md) | Your own tunnel for use anywhere: Cloudflare step by step, other tunnels, troubleshooting |
| [docs/setup.md](docs/setup.md) | The development machine's setup log (maintainer notes) |
| [docs/protocol.md](docs/protocol.md) | The phone ↔ computer protocol |
| [docs/threat-model.md](docs/threat-model.md) | Security model |
| [PROJECT-GUIDE.md](PROJECT-GUIDE.md) | Architecture, decisions and milestone progress |
| [AGENT_MONITOR_SPEC.md](AGENT_MONITOR_SPEC.md) | The product specification |
