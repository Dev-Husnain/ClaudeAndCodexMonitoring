package com.claude.codex.ai.monitoring.desktop.projects

import com.claude.codex.ai.monitoring.desktop.storage.AppStorage
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectNamerTest {

    private fun folder(name: String) = Files.createTempDirectory("agentmon-names").resolve(name).createDirectories()

    @Test
    fun `slugs read as words, names with their own capitals are kept`() {
        assertEquals("Agentmon Hooktest", ProjectNamer.humanize("agentmon-hooktest"))
        assertEquals("My Shop App", ProjectNamer.humanize("my_shop.app"))
        assertEquals("Website", ProjectNamer.humanize("website"))
        assertEquals("ClaudeMonitoring", ProjectNamer.humanize("ClaudeMonitoring"))
        assertEquals("ShopKart Android", ProjectNamer.humanize("ShopKart-android"))
    }

    @Test
    fun `the IDE's project name wins, then the build file, then the folder`() {
        val gradle = folder("shop-kart")
        gradle.resolve("settings.gradle.kts").writeText("pluginManagement {}\nrootProject.name = \"ShopKart\"\ninclude(\":app\")\n")
        assertEquals("ShopKart", ProjectNamer.nameFor(gradle))

        gradle.resolve(".idea").createDirectories().resolve(".name").writeText("ShopKart Seller\n")
        assertEquals("ShopKart Seller", ProjectNamer.nameFor(gradle))

        val node = folder("web")
        node.resolve("package.json").writeText("""{ "name": "@acme/admin-dashboard", "version": "1.0.0" }""")
        assertEquals("Admin Dashboard", ProjectNamer.nameFor(node))

        val python = folder("py")
        python.resolve("pyproject.toml").writeText("[build-system]\nrequires = []\n\n[project]\nname = \"invoice-parser\"\n")
        assertEquals("Invoice Parser", ProjectNamer.nameFor(python))

        assertEquals("Agentmon Hooktest", ProjectNamer.nameFor(folder("agentmon-hooktest")))
    }

    @Test
    fun `monitored projects get the readable name, also ones added before`() {
        val dir = folder("agentmon-hooktest")
        val store = ProjectStore(AppStorage.openDatabase(null))
        assertEquals("Agentmon Hooktest", store.add(dir).name)
        dir.resolve(".idea").createDirectories().resolve(".name").writeText("Hook Lab")
        // A second add reloads the list, which names projects afresh.
        store.add(folder("other"))
        assertEquals("Hook Lab", store.projects.value.first { it.projectId == store.projectFor(dir.toString())?.projectId }.name)
    }
}
