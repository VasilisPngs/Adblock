package com.vasilispngs.adblock.data

import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Looper
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowPackageInstaller
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [SilentUpdateTest.Installer::class, SilentUpdateTest.Session::class])
class SilentUpdateTest {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun reset() {
        Installer.requested.clear()
        Session.statuses.clear()
    }

    @Test
    fun `asks the user when the system refuses to update without asking`() = runTest {
        Session.statuses += listOf(status(PackageInstaller.STATUS_FAILURE_ABORTED, "INSTALL_FAILED_ABORTED: Permission denied"))
        Session.statuses += listOf(status(PackageInstaller.STATUS_SUCCESS))

        assertNull(commit())
        assertEquals(
            listOf(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED, PackageInstaller.SessionParams.USER_ACTION_REQUIRED),
            Installer.requested
        )
    }

    @Test
    fun `does not ask again after the user cancels`() = runTest {
        Session.statuses += listOf(
            status(PackageInstaller.STATUS_PENDING_USER_ACTION).putExtra(Intent.EXTRA_INTENT, Intent(CONFIRM)),
            status(PackageInstaller.STATUS_FAILURE_ABORTED, "INSTALL_FAILED_ABORTED: User rejected permissions")
        )

        assertEquals(SelfUpdate.Failed(ErrorCode.Named("INSTALL_FAILED_ABORTED")), commit())
        assertEquals(listOf(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED), Installer.requested)
    }

    private suspend fun TestScope.commit(): SelfUpdate.Failed? {
        File(context.cacheDir, "Adblock.apk").writeText("apk")
        val updater = SelfUpdater(context, backgroundScope) { url: URL -> url.openConnection() }
        val result = async { updater.commit() }
        while (!result.isCompleted) {
            shadowOf(Looper.getMainLooper()).idle()
            yield()
        }
        return result.await()
    }

    private fun status(code: Int, message: String? = null) =
        Intent().putExtra(PackageInstaller.EXTRA_STATUS, code).putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, message)

    @Implements(PackageInstaller::class)
    class Installer : ShadowPackageInstaller() {
        @Implementation
        override fun createSession(params: PackageInstaller.SessionParams): Int {
            requested += ReflectionHelpers.getField<Int>(params, "requireUserAction")
            return super.createSession(params)
        }

        companion object {
            val requested = mutableListOf<Int>()
        }
    }

    @Implements(PackageInstaller.Session::class)
    class Session : ShadowPackageInstaller.ShadowSession() {
        @Implementation
        override fun commit(statusReceiver: IntentSender) {
            statuses.removeFirst().forEach { statusReceiver.sendIntent(RuntimeEnvironment.getApplication(), 0, it, null, null) }
        }

        companion object {
            val statuses = ArrayDeque<List<Intent>>()
        }
    }

    private companion object {
        const val CONFIRM = "android.content.pm.action.CONFIRM_INSTALL"
    }
}
