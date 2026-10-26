package com.vasilispngs.adblock.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vasilispngs.adblock.R
import com.vasilispngs.adblock.data.AdblockDatabase
import com.vasilispngs.adblock.data.AppPreferences
import com.vasilispngs.adblock.data.Appearance
import com.vasilispngs.adblock.data.Blocklists
import com.vasilispngs.adblock.data.ProtectionPreferences
import com.vasilispngs.adblock.data.SelfUpdater
import com.vasilispngs.adblock.data.ThemeMode
import com.vasilispngs.adblock.vpn.DnsTunnel
import com.vasilispngs.adblock.vpn.DnsVpnService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity
import org.robolectric.shadows.ShadowVpnService
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class NotificationPermissionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val composeRule = compose.drainingMainLooper()

    private val context = RuntimeEnvironment.getApplication()
    private val database = AdblockDatabase.create(context)
    private val protection = hasText(context.getString(R.string.protection)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    @After
    fun close() = database.close()

    @Test
    fun `asks to show the notification the first time protection is turned on`() {
        show()
        compose.onNode(protection).performClick()

        val request = compose.runOnIdle { shadowOf(compose.activity).lastRequestedPermission }
        assertArrayEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS), request.requestedPermissions)

        compose.runOnIdle { decline(request) }
        awaitService()
    }

    @Test
    fun `turns on at once when notifications are allowed`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        show()
        compose.onNode(protection).performClick()

        awaitService()
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
    }

    @Test
    fun `does not ask again once notifications were declined`() {
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)
        show()
        compose.onNode(protection).performClick()

        awaitService()
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
    }

    private fun awaitService() {
        compose.awaitCondition { shadowOf(context).peekNextStartedService()?.component?.className == DnsVpnService::class.java.name }
    }

    private fun show() {
        ShadowVpnService.setPrepareResult(null)
        val preferences = ProtectionPreferences(PreferenceDataStoreFactory.create { File(folder.root, "protection.preferences_pb") })
        val appPreferences = AppPreferences(context, PreferenceDataStoreFactory.create { File(folder.root, "app.preferences_pb") })
        val home = HomeViewModel(DnsTunnel(context), preferences, MutableStateFlow(null), database.rules(), database.queries())
        val protectionViewModel = ProtectionViewModel(
            Blocklists(File(context.filesDir, "lists"), database.sources(), { throw IOException("Offline") }, {}),
            database.sources(),
            database.rules(),
            preferences,
            { _, _, onResult -> onResult(null) }
        )
        val settings = SettingsViewModel(appPreferences, preferences, SelfUpdater(context, MainScope()) { throw IOException("Offline") })
        compose.setContent {
            AdblockApp(home, protectionViewModel, settings, Appearance(ThemeMode.System, dynamicColor = false))
        }
        compose.awaitCondition { home.uiState.value.loaded }
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

}
