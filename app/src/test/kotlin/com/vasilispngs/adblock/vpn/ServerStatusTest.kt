package com.vasilispngs.adblock.vpn

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ServerStatusTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `shows the whole failure with its code when the notification expands`() {
        val extras = notification(DohResult.Rejected(502)).extras

        assertEquals(FAILURE, extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(FAILURE, extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
    }

    @Test
    fun `shows only the server while it answers`() {
        val extras = notification(null).extras

        assertEquals(HOST, extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertNull(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
    }

    private fun notification(failure: DohResult?): Notification =
        Notification.Builder(context, "dns").serverStatus(HOST, failure, context.resources).build()

    private companion object {
        const val HOST = "adblock.example.workers.dev"
        const val FAILURE = "adblock.example.workers.dev isn't answering (HTTP 502)"
    }
}
