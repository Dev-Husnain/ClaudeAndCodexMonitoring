AgentMon for Windows
====================

This folder holds two programs. Both carry their own Java, so nothing else needs installing.

  AgentMon\AgentMon.exe   The desktop agent. Keep it running; it lives in the tray.
  cli\agentmon.exe        The wrapper. Run "agentmon claude" instead of "claude" so the phone
                          can see and type into that terminal.

Quick start
-----------
1. Move this folder somewhere permanent, e.g. C:\Users\<you>\AgentMon.
2. Start AgentMon\AgentMon.exe. Pair your phone from the Devices tab (scan the QR code with the app).
3. Add the cli folder to your PATH (Start > "Edit environment variables for your account" >
   Path > Edit > New > paste the full path of the cli folder), then open a new terminal.
4. In your project folder run:   agentmon claude
   The session appears on the phone at once.
5. Optional: on the agent's Overview tab turn on "Start with Windows".

Full guide: https://github.com/Dev-Husnain/ClaudeAndCodexMonitoring#readme
