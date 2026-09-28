@echo off
rem Starts the AgentMon desktop agent (tray app + server on 127.0.0.1:8787).
rem The Cloudflare tunnel runs separately as the "cloudflared" Windows service.
cd /d "%~dp0.."
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
call gradlew.bat :desktop:run
