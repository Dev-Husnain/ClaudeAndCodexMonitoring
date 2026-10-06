# Builds the Windows release files into dist\:
#   AgentMon-<version>-windows-x64.zip   the desktop agent (AgentMon\AgentMon.exe) and the wrapper (cli\agentmon.exe),
#                                        each with its own Java runtime, so nothing else needs installing
#   AgentMon-<version>-android.apk       the phone app
#
# Needs a JDK 17+ that includes jpackage (Android Studio's JBR does not). Point JPACKAGE_JDK at it, for example:
#   $env:JPACKAGE_JDK = "C:\Program Files\Java\jdk-24"; .\scripts\package-release.ps1
param(
    [string]$Version = "1.0.6"
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$jdk = $env:JPACKAGE_JDK
if (-not $jdk) {
    $jdk = Get-ChildItem "C:\Program Files\Java", "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\jpackage.exe") } |
        Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName
}
$jpackage = Join-Path $jdk "bin\jpackage.exe"
if (-not (Test-Path $jpackage)) { throw "No jpackage found. Install a JDK 17+ and set JPACKAGE_JDK to its folder." }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }

Write-Host "Building with Gradle..."
& .\gradlew.bat :desktop:stageRelease :cli:installDist :app:assembleDebug -q
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

$dist = Join-Path $root "dist"
$work = Join-Path $dist "work"
Remove-Item $dist -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $work | Out-Null
$image = Join-Path $work "AgentMon-$Version-windows-x64"
New-Item -ItemType Directory -Force $image | Out-Null

# Only the Java modules the programs use (from jdeps), plus charsets for Windows code pages and naming for DNS.
$slim = "--strip-debug --no-man-pages --no-header-files --compress=zip-6"
$common = "java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.xml,jdk.charsets,jdk.unsupported"

Write-Host "Packaging the desktop agent..."
& $jpackage --type app-image --dest $image --name AgentMon --app-version $Version `
    --vendor "AgentMon" --description "Monitor and control Claude Code from your phone" `
    --input "desktop\build\release\agent" --main-jar desktop.jar `
    --main-class com.claude.codex.ai.monitoring.desktop.MainKt `
    --java-options "--enable-native-access=ALL-UNNAMED" `
    --add-modules "$common,java.net.http,java.sql" --jlink-options $slim
if ($LASTEXITCODE -ne 0) { throw "jpackage (desktop) failed" }

Write-Host "Packaging the agentmon wrapper..."
# Built next to the agent, then moved: Windows treats "AgentMon" and "agentmon" as the same folder name.
$cliWork = Join-Path $work "cli"
& $jpackage --type app-image --dest $cliWork --name agentmon --app-version $Version `
    --vendor "AgentMon" --description "Runs Claude Code so your phone can see and type into it" `
    --input "cli\build\install\agentmon\lib" --main-jar cli.jar `
    --main-class com.claude.codex.ai.monitoring.cli.MainKt `
    --java-options "--enable-native-access=ALL-UNNAMED" --java-options "-Xss2m" --win-console `
    --add-modules $common --jlink-options $slim
if ($LASTEXITCODE -ne 0) { throw "jpackage (wrapper) failed" }
Move-Item (Join-Path $cliWork "agentmon") (Join-Path $image "cli")

Copy-Item "scripts\release-readme.txt" (Join-Path $image "README.txt")
Compress-Archive -Path $image -DestinationPath (Join-Path $dist "AgentMon-$Version-windows-x64.zip")
Copy-Item "app\build\outputs\apk\debug\app-debug.apk" (Join-Path $dist "AgentMon-$Version-android.apk")
Remove-Item $work -Recurse -Force

Write-Host "Done:"
Get-ChildItem $dist | ForEach-Object { "  {0}  ({1:N1} MB)" -f $_.Name, ($_.Length / 1MB) }
