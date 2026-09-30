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
