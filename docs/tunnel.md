# Use AgentMon from anywhere: your own tunnel

Over a USB cable the phone reaches your computer through `adb reverse`. To use the phone **anywhere** (mobile
data, another Wi-Fi), the phone needs a public `https://` address that leads to the agent on your computer.

The agent itself never opens a port to the network: it listens on `127.0.0.1:8787` only. A **tunnel** program
on your computer makes an outgoing connection to a tunnel service and passes requests for your address to
`http://localhost:8787`. You don't open router ports, and you don't need a fixed IP address.

```
Phone ──https──► tunnel service (e.g. Cloudflare) ◄──outgoing connection── tunnel program on your PC ──► 127.0.0.1:8787 (AgentMon)
```

## Contents

1. [What any tunnel must do](#what-any-tunnel-must-do)
2. [Option A: Cloudflare Tunnel (recommended, free)](#option-a-cloudflare-tunnel-recommended-free)
3. [Option B: other tunnels (ngrok, Tailscale Funnel)](#option-b-other-tunnels-ngrok-tailscale-funnel)
4. [Tell AgentMon your address](#tell-agentmon-your-address)
5. [Pair the phone for "Anywhere"](#pair-the-phone-for-anywhere)
6. [Changing the address later](#changing-the-address-later)
7. [Security notes](#security-notes)
8. [Troubleshooting](#troubleshooting)

---

## What any tunnel must do

Whichever tunnel you use, it has to:

| Requirement | Why |
|---|---|
| Give an **`https://`** address | The phone refuses plain `http://` to anything except the USB route |
| Forward to **`http://localhost:8787`** | That is where the agent listens |
| Pass **WebSockets** through | The phone's live connection is a WebSocket (`/ws`) |
| Keep the **same address** | The address is stored in the phone at pairing; a new address means pairing again |
| Not put a **login page** in front | The app can't get through a browser sign-in screen |

You don't need: open ports, port forwarding on the router, a static IP, or firewall changes.

---

## Option A: Cloudflare Tunnel (recommended, free)

This is what AgentMon is built and tested with. You need a domain whose DNS is managed by Cloudflare. The free
plan is enough.

### 1. Get a domain onto Cloudflare

1. If you don't have a domain, buy one from any registrar (Cloudflare Registrar, Namecheap, Porkbun…). Free
   subdomain services also work, as long as they let you change the nameservers.
2. Sign in at [dash.cloudflare.com](https://dash.cloudflare.com), click **Add a domain**, enter your domain and pick
   the **Free** plan.
3. Cloudflare shows two **nameservers**. At your registrar, replace the domain's nameservers with those two.
4. Wait until Cloudflare marks the domain **Active**. This takes minutes to a few hours, and Cloudflare emails you.

### 2. Install cloudflared

In PowerShell:

```powershell
winget install --id Cloudflare.cloudflared
```

Then **open a new terminal** so `cloudflared` is found. Check with `cloudflared --version`.

### 3. Log in and create the tunnel

```powershell
cloudflared tunnel login
```

A browser opens. Pick your domain and authorise. This saves `%USERPROFILE%\.cloudflared\cert.pem`.

```powershell
cloudflared tunnel create agentmon
```

Note the **tunnel ID** it prints (a long id like `95be45b0-…`). It also creates
`%USERPROFILE%\.cloudflared\<TUNNEL-ID>.json`. **Keep that file private**: it lets anyone run your tunnel.

### 4. Give the tunnel a hostname

Choose a name under your domain, for example `agent.example.com`:

```powershell
cloudflared tunnel route dns agentmon agent.example.com
```

This creates the DNS record in Cloudflare for you.

### 5. Write the configuration

Create `%USERPROFILE%\.cloudflared\config.yml`. Replace the tunnel ID, your Windows user name and the hostname:

```yaml
tunnel: <TUNNEL-ID>
credentials-file: C:\Users\<you>\.cloudflared\<TUNNEL-ID>.json
ingress:
  - hostname: agent.example.com
    service: http://localhost:8787
  - service: http_status:404
```

The last line refuses anything that isn't your hostname.

### 6. Test it in the foreground

Make sure AgentMon is running, then:

```powershell
cloudflared tunnel run agentmon
```

On the phone, **turn off Wi-Fi** and open `https://agent.example.com/health` in the browser. It should show:

```
{"status":"ok","version":"…"}
```

Stop the test with **Ctrl+C**.

### 7. Run the tunnel as a Windows service

Then the tunnel starts with Windows, before anyone signs in.

1. Open **PowerShell as Administrator** (right-click → Run as administrator) and run:
   ```powershell
   cloudflared service install
   ```
2. The service runs as the SYSTEM account, which has its own profile folder. Copy your config and credentials
   there:
   ```powershell
   $sys = "C:\Windows\System32\config\systemprofile\.cloudflared"
   New-Item -ItemType Directory -Force $sys
   Copy-Item "$env:USERPROFILE\.cloudflared\config.yml", "$env:USERPROFILE\.cloudflared\<TUNNEL-ID>.json" $sys
   ```
3. Edit `C:\Windows\System32\config\systemprofile\.cloudflared\config.yml` and change `credentials-file` to the
   new location:
   ```yaml
   credentials-file: C:\Windows\System32\config\systemprofile\.cloudflared\<TUNNEL-ID>.json
   ```
4. **Known problem:** on some Windows installs, the service starts without arguments and stops at once. Give it
   its full command line (still as Administrator). Adjust the path if `cloudflared.exe` is elsewhere; find it with
   `(Get-Command cloudflared).Source`:
   ```powershell
   $exe = (Get-Command cloudflared).Source
   $cfg = "C:\Windows\System32\config\systemprofile\.cloudflared"
   Set-ItemProperty "HKLM:\SYSTEM\CurrentControlSet\Services\cloudflared" -Name ImagePath `
       -Value "`"$exe`" --config `"$cfg\config.yml`" --logfile `"$cfg\cloudflared.log`" tunnel run"
   Set-Service cloudflared -StartupType Automatic
   Restart-Service cloudflared
   ```
5. Check `https://agent.example.com/health` again from the phone on mobile data. Restart the computer once and
   check again.

If the computer says "could not resolve host" right after setup, your router is still caching the old DNS
answer. It clears within about 30 minutes; the phone on mobile data usually works sooner.

---

## Option B: other tunnels (ngrok, Tailscale Funnel)

Any tunnel that meets [the requirements](#what-any-tunnel-must-do) should work. These are **not tested** with
AgentMon yet; Cloudflare is.

AgentMon recognises tunnel traffic by the forwarding headers these services add (`X-Forwarded-For`, `X-Real-IP`,
`Forwarded`) and Cloudflare's own. It refuses hook calls and terminal links that arrive that way, and uses the
real client address for rate limiting.

### ngrok

1. Create an account at [ngrok.com](https://ngrok.com), install the agent (`winget install ngrok.ngrok`), and add
   your authtoken: `ngrok config add-authtoken <token>`.
2. Claim your free **static domain** in the ngrok dashboard (Domains). A random address would change on every
   start and break pairing.
3. Run:
   ```powershell
   ngrok http 8787 --url=your-name.ngrok-free.app
   ```
4. Your address is `your-name.ngrok-free.app`. To start it with Windows, see ngrok's docs on
   `ngrok service install`.

### Tailscale Funnel

1. Install Tailscale on the computer, sign in, and enable **HTTPS** and **Funnel** for your tailnet in the admin
   console.
2. Run `tailscale funnel --bg 8787`.
3. Your address is the computer's name, for example `my-pc.tail1234.ts.net`. Funnel is public, so the phone
   doesn't need Tailscale installed.

### What does **not** work

- **Opening port 8787 on your router.** The agent only listens on `127.0.0.1` on purpose, so nothing outside the
  computer can reach it directly.
- **Plain `http://` addresses.** The phone refuses them.
- **Cloudflare Access / Zero Trust login, or any login page** in front of the address. The app can't sign in
  through a browser page. AgentMon already only lets paired phones in.

---

## Tell AgentMon your address

1. Open the AgentMon window (double-click the tray icon) and go to **Overview**.
2. Under **This computer → Phone access address**, type your hostname, for example `agent.example.com`.
   You can leave out `https://`.
3. Click **Save**.

The address is checked before it is saved. AgentMon accepts only an `https` hostname with no path, and rejects
`http://`, `localhost` and bare IP addresses. It is saved in `%APPDATA%\AgentMon\settings.properties`,
so restarts and updates keep it.

Advanced: the environment variable `AGENTMON_PUBLIC_URL`, or `AgentMon.exe --public-url https://agent.example.com`,
override the saved address. The field then shows where the address comes from and can't be edited.

---

## Pair the phone for "Anywhere"

1. In AgentMon, click **Pair device**. With an address saved, the dialog uses **Anywhere · your-address**. It also
   checks the address from this computer and shows a red note if that fails. Pairing still works if
   `https://<your address>/health` opens in the phone's browser.
2. Scan the QR code with the app, compare the fingerprints, and approve on the computer.
3. Test it: turn off Wi-Fi on the phone. The app should still show **Connected**.

A phone paired over **USB** has the USB address stored. Pair it again with **Anywhere** to use it away from home.

---

## Changing the address later

If you move to a new hostname or another tunnel service:

1. Set the new tunnel up and check `https://<new address>/health`.
2. Save the new address under **Phone access address**.
3. Pair each phone again with **Anywhere**. Phones keep the address they were paired with.
4. Remove the old phone entries in **Devices** if they are no longer used.

---

## Security notes

- Only **paired phones** can use the address. Each phone proves its identity with a key kept in Android's
  Keystore. The address alone gives a stranger nothing but `/health` and a pairing endpoint that needs a fresh,
  2-minute, single-use code from your screen.
- Claude's hooks and the `agentmon claude` terminal link are **refused** when they come through any tunnel.
- Keep `cert.pem` and `<TUNNEL-ID>.json` private, and never commit them to git.
- To cut remote access at once, stop the tunnel (`Stop-Service cloudflared`) or revoke the phone in **Devices**.

---

## Troubleshooting

| What you see | What to check |
|---|---|
| `/health` gives **502 / Bad gateway** | AgentMon isn't running, or `config.yml` points somewhere other than `http://localhost:8787` |
| `/health` gives **404** | The hostname in `config.yml` doesn't match the one you open |
| `/health` can't be **found** (DNS) | `cloudflared tunnel route dns …` wasn't run, the domain isn't **Active** on Cloudflare yet, or your router still caches the old answer (try mobile data) |
| Works in the foreground, not after a restart | The Windows service: redo step 7.2–7.4 and check `C:\Windows\System32\config\systemprofile\.cloudflared\cloudflared.log` |
| Pairing dialog only offers **USB** | No address is saved. Save it under **Phone access address** and open **Pair device** again |
| Pairing dialog warns that this computer can't reach the address | Usually this computer's DNS, not the tunnel. Check `/health` on the phone; to clear the warning, set the computer's DNS to `1.1.1.1` |
| Phone says **Offline** away from home | The phone was paired over USB: pair again with **Anywhere**. Or the tunnel is down: check `/health` |
| Sessions stopped updating after you set up a proxy | Claude's hooks must reach `127.0.0.1` directly. If you use `HTTP_PROXY`/`HTTPS_PROXY`, set `NO_PROXY=127.0.0.1,localhost` |
