@echo off
rem Installs the AgentMon desktop agent to %LOCALAPPDATA%\AgentMon\agent. Quit a running agent first (tray: Quit).
cd /d "%~dp0.."
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
call gradlew.bat :desktop:installAgent || exit /b 1
echo.
echo Installed. Start it with: "%LOCALAPPDATA%\AgentMon\agent\AgentMon.cmd"
echo Then turn on "Start with Windows" on its Overview screen.
