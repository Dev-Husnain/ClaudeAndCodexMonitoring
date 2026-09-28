package com.claude.codex.ai.monitoring.domain.usecase

import com.claude.codex.ai.monitoring.domain.models.ServerUrlError
import com.claude.codex.ai.monitoring.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdateServerUrlUseCaseTest {

    private val settings = FakeSettingsRepository()
    private val useCase = UpdateServerUrlUseCase(settings)

    @Test
    fun `wss to the tunnel host is saved`() = runTest {
        val result = useCase("  wss://agent.appsdev.qzz.io/ws ")
        assertTrue(result.isSuccess)
        assertEquals("wss://agent.appsdev.qzz.io/ws", settings.settings.value.serverUrl)
    }

    @Test
    fun `plain ws is allowed only for loopback`() = runTest {
        assertTrue(useCase("ws://127.0.0.1:8787/ws").isSuccess)
        assertTrue(useCase("ws://localhost:8787/ws").isSuccess)
        assertIs<ServerUrlError.Insecure>(useCase("ws://192.168.1.5:8787/ws").exceptionOrNull())
    }

    @Test
    fun `malformed or wrong path is invalid and not saved`() = runTest {
        val before = settings.settings.value.serverUrl
        assertIs<ServerUrlError.Invalid>(useCase("http://127.0.0.1:8787/ws").exceptionOrNull())
        assertIs<ServerUrlError.Invalid>(useCase("wss://agent.appsdev.qzz.io/").exceptionOrNull())
        assertIs<ServerUrlError.Invalid>(useCase("not a url").exceptionOrNull())
        assertEquals(before, settings.settings.value.serverUrl)
    }
}
