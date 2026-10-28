package com.vasilispngs.adblock.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.AppPreferences
import com.vasilispngs.adblock.data.DnsPreferences
import com.vasilispngs.adblock.data.SelfUpdater
import com.vasilispngs.adblock.data.ThemeMode
import com.vasilispngs.adblock.vpn.DnsTunnel
import com.vasilispngs.adblock.vpn.DohResult
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class NotificationPermissionTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = RuntimeEnvironment.getApplication()
    private val asked = mutableListOf<String>()

    @Test
    fun `asks to show the notification the first time DNS-over-HTTPS is turned on`() {
        show()
        compose.onNodeWithText(context.getString(R.string.dns_over_https)).performClick()

        val request = compose.runOnIdle { shadowOf(compose.activity).lastRequestedPermission }
        assertArrayEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS), request.requestedPermissions)

        compose.runOnIdle { decline(request) }
        compose.waitUntil { asked.isNotEmpty() }
        assertEquals(listOf(SERVER), asked)
    }

    @Test
    fun `turns on at once when notifications are allowed`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        show()
        compose.onNodeWithText(context.getString(R.string.dns_over_https)).performClick()

        compose.waitUntil { asked.isNotEmpty() }
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
    }

    @Test
    fun `does not ask again once notifications were declined`() {
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)
        show()
        compose.onNodeWithText(context.getString(R.string.dns_over_https)).performClick()

        compose.waitUntil { asked.isNotEmpty() }
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
    }

    private fun show() {
        val preferences = DnsPreferences(PreferenceDataStoreFactory.create { File(folder.root, "dns.preferences_pb") })
        runBlocking { preferences.setUrl(SERVER) }
        val appPreferences = AppPreferences(
            context,
            PreferenceDataStoreFactory.create { File(folder.root, "app.preferences_pb") }
        )
        val viewModel = DnsViewModel(
            preferences,
            appPreferences,
            DnsTunnel(context),
            MutableStateFlow(null),
            SelfUpdater(context, MainScope()) { throw IOException("Offline") }
        ) { server, _ ->
            asked += server
            DohResult.Failed
        }
        compose.setContent {
            DnsScreen(viewModel = viewModel, themeMode = ThemeMode.System)
        }
        compose.waitUntil { compose.onAllNodes(hasText(SERVER)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun decline(request: ShadowActivity.PermissionsRequest) {
        compose.activity.activityResultRegistry.dispatchResult(
            request.requestCode,
            Activity.RESULT_OK,
            Intent()
                .putExtra(RequestMultiplePermissions.EXTRA_PERMISSIONS, request.requestedPermissions)
                .putExtra(RequestMultiplePermissions.EXTRA_PERMISSION_GRANT_RESULTS, intArrayOf(PackageManager.PERMISSION_DENIED))
        )
    }

    private companion object {
        const val SERVER = "https://dns.nextdns.io/abc123"
    }
}
