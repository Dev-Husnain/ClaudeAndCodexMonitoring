@echo off
rem Starts the installed AgentMon desktop agent (tray app + server on 127.0.0.1:8787) without Gradle.
rem Pass --background to start in the tray with the window hidden.
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
start "" "%JAVA_HOME%\bin\javaw.exe" --enable-native-access=ALL-UNNAMED -cp "%~dp0lib\*" com.claude.codex.ai.monitoring.desktop.MainKt %*
