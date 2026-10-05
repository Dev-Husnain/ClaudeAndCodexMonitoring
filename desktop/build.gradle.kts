import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.sqldelight)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.slf4j.simple)
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.zxing.core)
    implementation(libs.jediterm.core)
    implementation(libs.jna)
    implementation(libs.jna.platform)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
}

sqldelight {
    databases {
        create("AgentMonDatabase") {
            packageName.set("com.claude.codex.ai.monitoring.desktop.db")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.claude.codex.ai.monitoring.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "AgentMon"
            packageVersion = "1.0.0"
        }
    }
}

compose.resources {
    packageOfResClass = "com.claude.codex.ai.monitoring.desktop.resources"
    publicResClass = false
}

/**
 * Installs the agent to %LOCALAPPDATA%\AgentMon\agent (or ~/.agentmon/agent): its jars plus AgentMon.cmd, so it
 * starts in seconds without Gradle and can be registered to start with Windows. Quit a running agent first:
 * Windows keeps its jars locked.
 */
val installAgent by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Installs the desktop agent for running without Gradle and starting with Windows."
    val target = System.getenv("LOCALAPPDATA")?.let { file("$it/AgentMon/agent") }
        ?: file("${System.getProperty("user.home")}/.agentmon/agent")
    from(tasks.named("jar")) { into("lib") }
    from(configurations.named("runtimeClasspath")) { into("lib") }
    from(layout.projectDirectory.dir("src/dist"))
    into(target)
}

/** The agent's jars for `scripts/package-release.ps1`, which turns them into `AgentMon.exe` with its own Java. */
val stageRelease by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Collects the desktop agent's jars for packaging a release."
    from(tasks.named("jar"))
    from(configurations.named("runtimeClasspath"))
    into(layout.buildDirectory.dir("release/agent"))
}
