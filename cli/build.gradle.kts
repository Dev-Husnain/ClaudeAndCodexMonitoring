import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

application {
    applicationName = "agentmon"
    mainClass = "com.claude.codex.ai.monitoring.cli.MainKt"
    // JNA (pty4j) and the JLine JNI provider load native code.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED", "-Xss2m")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.pty4j)
    implementation(libs.jline.terminal.jni)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    // pty4j and Ktor log through SLF4J; nothing may be printed over Claude's screen.
    runtimeOnly(libs.slf4j.nop)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

/**
 * Installs the wrapper to %LOCALAPPDATA%\AgentMon\cli (or ~/.agentmon/cli) for everyday use. Put its bin folder on
 * PATH, not build/install: a build replaces the jars there under a running wrapper, which then fails the first time
 * it loads a class it had not needed yet (typing from the phone is the usual one). Quit running wrappers first.
 */
val installWrapper by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Installs the agentmon wrapper outside the build folder, for PATH."
    val target = System.getenv("LOCALAPPDATA")?.let { file("$it/AgentMon/cli") }
        ?: file("${System.getProperty("user.home")}/.agentmon/cli")
    from(tasks.named("installDist"))
    into(target)
}
