package com.claude.codex.ai.monitoring.core.utils

import com.claude.codex.ai.monitoring.R
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {

    private val now = 10_000_000_000L

    @Test
    fun `under a minute and future times read as just now`() {
        assertEquals(UiText.Res(R.string.time_just_now), (now - 59_000).toRelativeTime(now))
        assertEquals(UiText.Res(R.string.time_just_now), (now + 5_000).toRelativeTime(now))
    }

    @Test
    fun `minutes hours and days use plurals`() {
        assertEquals(UiText.Plural(R.plurals.time_minutes_ago, 5), (now - 5 * 60_000).toRelativeTime(now))
        assertEquals(UiText.Plural(R.plurals.time_hours_ago, 3), (now - 3 * 3_600_000).toRelativeTime(now))
        assertEquals(UiText.Plural(R.plurals.time_days_ago, 2), (now - 2 * 86_400_000L).toRelativeTime(now))
    }
}
