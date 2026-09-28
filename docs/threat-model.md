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
| Cloudflare edge → agent | Mutual device authentication (P-256 challenge/response, pinned desktop key); Cloudflare only carries bytes. | Done (M3) |
| Other local processes → `/hook` | Loopback only plus a shared secret header. | Phase 4 |
| Phone at rest | Keystore key (StrongBox when available) is non-exportable; backups are disabled so pairing data never leaves the device. | Done (M3) |

## Known gaps (tracked in PROJECT-GUIDE.md)

1. Payloads are not end-to-end encrypted: the Cloudflare edge can read them (optional in phase 8,
   spec 6.5). Authentication is end to end: a compromised edge cannot impersonate either side.
2. Anyone who can photograph the pairing QR within its 2 minutes can send a pairing request, but
   nothing is granted until the owner approves it on the laptop and compares the key fingerprint.
3. A local process on the laptop could forge `CF-Connecting-IP` to dodge per-IP limits. Per-device
   limits still apply, and local processes are already trusted with far more.
4. `/hook` (phase 4) is not built yet.

## Rules that must not be weakened (spec §6, §14.6)

- Never bind the agent to `0.0.0.0` or a LAN address.
- Never skip or relax the challenge/signature check "to make it work".
- Never log prompt or terminal contents to disk unless the owner enables it.
