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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        Installer.failure = null
        Session.statuses.clear()
        File(context.noBackupFilesDir, UPDATED_MARKER).delete()
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

    @Test
    fun `reports failure when the system refuses to open a session`() = runTest {
        Installer.failure = SecurityException("Denied")

        assertEquals(SelfUpdate.Failed(ErrorCode.Named("SecurityException")), commit())
    }

    @Test
    fun `confirms a self-update only when the installed APK matches the expected digest`() {
        val source = File(context.cacheDir, "installed.apk").apply { writeText("installed-apk") }
        context.applicationInfo.sourceDir = source.path
        val marker = File(context.noBackupFilesDir, UPDATED_MARKER)

        marker.writeText("sha256:stale")
        assertFalse(context.consumeSelfUpdate())
        assertFalse(marker.exists())

        marker.writeText(source.inputStream().use(::sha256))
        assertTrue(context.consumeSelfUpdate())
        assertFalse(marker.exists())
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
            failure?.let { throw it }
            requested += ReflectionHelpers.getField<Int>(params, "requireUserAction")
            return super.createSession(params)
        }

        companion object {
            val requested = mutableListOf<Int>()
            var failure: RuntimeException? = null
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
