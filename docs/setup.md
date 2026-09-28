# AgentMon: owner setup (Windows)

Each part says when it is needed. Parts A and B are enough to run the current build.

## A. Development machine (needed now)

1. **JDK.** Use the JDK that ships with Android Studio. In a terminal:
   `setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"`, then open a new terminal.
   Check with `"%JAVA_HOME%\bin\java" -version`, which should report 21.
2. **Git** (`winget install Git.Git`). The repo is already initialised on branch `feat/m0-foundation`.
3. **Claude Code** installed and logged in. Running `claude` in the Android Studio terminal should work.
   It isn't needed until phase 4.
4. **Android SDK platform-tools** (adb) come with Android Studio. They live at
   `%LOCALAPPDATA%\Android\Sdk\platform-tools`, so add that folder to `PATH`.

## B. Run it locally (phase 1)

1. Start the desktop agent from the project root:
   ```
   gradlew.bat :desktop:run
   ```
   A window titled **AgentMon** opens and a tray icon appears. Closing the window keeps it running in the
   tray; use **Quit** in the tray menu to stop it. It runs demo sessions by default (`--no-demo` turns them off).
   Check it with `curl http://127.0.0.1:8787/health`.
2. Connect a phone or emulator:
   - **Emulator:** start any AVD from Android Studio's Device Manager.
   - **Phone:** Settings → About phone → tap *Build number* 7 times → Developer options → turn on
     *USB debugging* → connect by USB → accept the prompt. `adb devices` should list the phone.
3. Forward the phone's `127.0.0.1:8787` to the laptop (run this again after each reconnect or reboot):
   ```
   adb reverse tcp:8787 tcp:8787
   ```
4. Install and open the app: `gradlew.bat :app:installDebug`, or press Run in Android Studio.
   The default address `ws://127.0.0.1:8787/ws` works as is. Within a few seconds the pill should show
   **Connected** with demo sessions.

Troubleshooting: if the app shows *Offline*, check `/health` on the laptop, run `adb reverse --list`,
then tap **Try again**.

## C. Cloudflare tunnel (phase 2: remote access over mobile data)

**Complete phase 3 (pairing and authentication) before you leave this tunnel running with real sessions.**
Until then the agent serves anyone who reaches the URL. It only has demo data, but don't rely on that.

1. **Put the domain on Cloudflare.** Log in at dash.cloudflare.com (the free plan is fine), click
   *Add a domain*, and enter `appsdev.qzz.io`. Cloudflare shows two nameservers. At the DigitalPlat domain
   dashboard, replace the domain's nameservers with those two. Wait until Cloudflare marks the site
   **Active** (minutes to hours).
2. Install cloudflared: `winget install --id Cloudflare.cloudflared`, then open a new terminal.
3. Log in: `cloudflared tunnel login`. A browser opens; pick `appsdev.qzz.io`. This creates
   `%USERPROFILE%\.cloudflared\cert.pem`.
4. Create the tunnel: `cloudflared tunnel create agentmon`. Note the printed **tunnel ID**. It creates
   `%USERPROFILE%\.cloudflared\<TUNNEL-ID>.json`.
5. Point DNS at it: `cloudflared tunnel route dns agentmon agent.appsdev.qzz.io`.
6. Create `%USERPROFILE%\.cloudflared\config.yml`, replacing both placeholders:
   ```yaml
   tunnel: <TUNNEL-ID>
   credentials-file: C:\Users\<you>\.cloudflared\<TUNNEL-ID>.json
   ingress:
     - hostname: agent.appsdev.qzz.io
       service: http://localhost:8787
     - service: http_status:404
   ```
7. Test it in the foreground: run `cloudflared tunnel run agentmon`, then open
   `https://agent.appsdev.qzz.io/health` on the phone over mobile data. It should show `{"status":"ok",...}`.
8. Make it start with Windows. In an **Administrator** terminal, run `cloudflared service install`. The
   service runs as SYSTEM and reads its config from
   `C:\Windows\System32\config\systemprofile\.cloudflared\`. Copy `config.yml` and `<TUNNEL-ID>.json`
   there and change `credentials-file` in the copied `config.yml` to the new path. Then run
   `sc stop cloudflared` followed by `sc start cloudflared`, and reboot once to confirm the health URL
   still works.
9. In the app, go to Settings → Desktop agent address → `wss://agent.appsdev.qzz.io/ws` → Save.
   (Phase 3 fills this in automatically from the pairing QR.)
10. Cloudflare Access (Zero Trust) is optional and deferred to phase 8.

## D. Later phases

- **Phase 4 (hooks):** choose a test project. The desktop app's *Add project* action writes hooks into
  that project's `.claude/settings.local.json`, which is never committed.
- **Phase 7 (notifications):** allow notifications for AgentMon, and turn off battery optimisation for it
  (Settings → Apps → AgentMon → Battery → Unrestricted; Xiaomi/MIUI also needs *Autostart*).
