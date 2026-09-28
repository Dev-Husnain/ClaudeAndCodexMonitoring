# AgentMon threat model

## Assets

- **Control of the owner's coding agent.** Input sent into a session can run commands on the laptop.
  This is the highest-value asset.
- Session content: prompts, tool names, file paths, snippets.
- Device keys: the phone's Keystore key and the desktop key.

## Trust boundaries

| Boundary | Protection | Status |
|---|---|---|
| LAN / internet → desktop agent | The server binds **127.0.0.1 only**. The only ways in are the outbound Cloudflare tunnel and USB `adb reverse`. | Done (M0/M1) |
| Phone ↔ Cloudflare edge | TLS (`wss://`). The app refuses `ws://` for any non-loopback host. | Done (M1) |
| Cloudflare edge → agent | Mutual device authentication; Cloudflare only carries bytes. | **Phase 3** |
| Other local processes → `/hook` | Loopback only plus a shared secret header. | Phase 4 |
| Phone at rest | Keystore key is non-exportable; backups are disabled so pairing data never leaves the device. | Backups done; key in phase 3 |

## Known gaps (tracked in PROJECT-GUIDE.md)

1. **No authentication yet (phases 0–2).** Any client that can reach `/ws` sees the current sessions.
   Today that means only this computer and USB-connected devices, and the sessions are **demo data**.
   *Do not expose real sessions through the tunnel before phase 3.* Real hook data arrives in
   phase 4, after authentication.
2. Payloads are not end-to-end encrypted, so the Cloudflare edge can see them (optional in phase 8,
   spec §6.5).
3. No rate limiting until phase 3.

## Rules that must not be weakened (spec §6, §14.6)

- Never bind the agent to `0.0.0.0` or a LAN address.
- Never skip or relax the challenge/signature check "to make it work".
- Never log prompt or terminal contents to disk unless the owner enables it.
