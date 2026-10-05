package com.claude.codex.ai.monitoring.desktop.devices

import com.claude.codex.ai.monitoring.desktop.storage.AppStorage
import kotlin.test.Test
import kotlin.test.assertEquals

class AuditLogTest {

    @Test
    fun `clearing empties the log but records that it was cleared`() {
        val log = AuditLog(AppStorage.openDatabase(null), clock = { 42L })
        log.record(AuditCategory.PAIRING, "Paired \"Pixel\"")
        log.record(AuditCategory.AUTH, "Signed in")
        log.clear()
        assertEquals(listOf("Activity log cleared"), log.entries.value.map { it.message })
        assertEquals(AuditCategory.SERVER, log.entries.value.single().category)
    }
}
