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

1. Start the desktop agent: double-click `scripts\start-agent.cmd`, or from the project root run:
   ```
   gradlew.bat :desktop:run
   ```
   The tunnel keeps running as a service, but it can only reach the phone while this agent is running
   (otherwise the health URL returns 502). Auto-start for the agent comes with packaging in phase 8.
   A window titled **AgentMon** opens and a tray icon appears. Closing the window keeps it running in the
   tray; use **Quit** in the tray menu to stop it. Real sessions come from the projects you add (see part D). `--demo` adds fake sessions for trying the phone.
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
5. **Pair** (one time per phone):
   - In the desktop window, click **Pair device**, then choose **USB · adb reverse** (use **Anywhere · tunnel**
     once part C is done).
   - On the phone, tap **Pair with your computer**, allow the camera, and scan the QR code. Without a camera,
     tap **Enter code instead** and paste the code shown under the QR.
   - Check that the **Computer key** on the phone matches the **Desktop key** in the dialog, name the phone,
     and tap **Send pairing request**.
   - An **Approve** window opens on the laptop. Check that the phone key matches, choose projects and
     whether the phone may send input (off means read-only), then click **Approve**.
   - The phone shows **Paired with …** and then the live sessions.
6. Manage phones in the desktop **Devices** screen: turn access on or off, or use **Revoke access**, which
   disconnects the phone at once. **Activity** shows every pairing, sign-in and change.

Troubleshooting: if pairing says it could not reach the computer, check `/health` on the laptop and run
`adb reverse --list`. If the code expired, click **New code** on the desktop.

## C. Cloudflare tunnel (phase 2: remote access over mobile data)

Phase 3 (pairing and authentication) is done, so only approved phones can use the tunnel.

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
   there and change `credentials-file` in the copied `config.yml` to the new path.
   **Gotcha (seen on this machine):** the installed service starts with no arguments and exits at once. Set
   its command line explicitly in the registry, `HKLM\SYSTEM\CurrentControlSet\Services\cloudflared\ImagePath` =
   `"C:\Program Files (x86)\cloudflared\cloudflared.exe" --config "C:\Windows\System32\config\systemprofile\.cloudflared\config.yml" --logfile "C:\Windows\System32\config\systemprofile\.cloudflared\cloudflared.log" tunnel run`.
   Then run `Start-Service cloudflared`. Check `https://agent.appsdev.qzz.io/health`.
   (Done on 2026-09-28: tunnel `agentmon`, id `95be45b0-40ea-4f27-b345-427818a58eac`, service set to Automatic.)
   If the PC says "could not resolve host" right after setup, the router's DNS is still caching the old
   "not found" answer; it clears within about 30 minutes.
9. Pair the phone with the **Anywhere · tunnel** option. The QR carries `https://agent.appsdev.qzz.io`, so
   the phone then works over mobile data. A phone paired over USB has to be paired again this way.
10. Cloudflare Access (Zero Trust) is optional and deferred to phase 8.

## D. Later phases

- **Phase 4: real sessions.** In the desktop app, go to **Projects**, click **Add project** and choose the folder you
  run Claude Code in (for example your Android Studio project). AgentMon adds its hooks to that project's
  `.claude/settings.local.json`, keeps your own hooks, and adds the file to `.gitignore` if needed. Start a
  new Claude session there, or restart a running one, and it shows up on the phone. **Remove** takes out
  only AgentMon's hooks. Demo sessions are off now; start the agent with `--demo` to see them.
- **Phase 5a: control from the phone (Away mode).**
  1. On the desktop, go to **Devices** and turn on **Allow sending input** for the phone. Read-only phones
     only watch.
  2. Before you leave the computer, turn on **Away mode** (phone Home, session screen, or the desktop
     sidebar). It is off after every restart of the agent, on purpose.
  3. When Claude asks for permission, the phone shows the session under **Needs you** with **Approve / Deny
     / Stop**. When Claude finishes, type the next step in **Message Claude…** (or tap **Continue** / **Let
     it stop**). A message sent while Claude is still working is delivered when it finishes that turn.
  4. Back at the computer, turn Away mode off: anything still waiting falls back to Claude's normal prompt.
- **Phase 5b: type into Claude's terminal from the phone (`agentmon claude`).**
  1. Build the wrapper once: `./gradlew :cli:installDist`. It lands in `cli\build\install\agentmon\bin`.
     Add that folder to your user PATH (Windows: Start → "Edit environment variables for your account" →
     Path → New), then open a new terminal.
  2. In a monitored project folder, start Claude with `agentmon claude` instead of `claude`. All `claude`
     arguments work (`agentmon claude --resume`, …). The desktop agent should be running; if it is not,
     Claude still starts and links up once the agent is back.
  3. On the phone the session shows **Controllable** and gets a **Terminal** tab: the live screen, keys
     (Enter, Esc, arrows, ⇧Tab, 1–3, Ctrl+C) and the message box, which types straight into Claude. Two
     fingers zoom the terminal.
  4. After you change the hooks version (agent update), restart running Claude sessions once so they
     pick up the new hook settings.
- **Phase 7 (notifications):** allow notifications for AgentMon, and turn off battery optimisation for it
  (Settings → Apps → AgentMon → Battery → Unrestricted; Xiaomi/MIUI also needs *Autostart*).
