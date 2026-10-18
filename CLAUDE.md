# Project Instructions

## Git & repository workflow
- Push changes directly to `main`; do not open pull requests.
- Publish every completed change set as exactly one commit.
- This file is shared by VasilisPngs/AppUpdater and VasilisPngs/Adblock: every change to it is made identically in both repositories at the same time.

## General rules
- Follow only Google's official guidance and rules: Material Design, the Android developer documentation and Google's own libraries and samples. When the user asks for something beyond them, first say plainly that it departs from Google and which guideline it breaks, and go ahead only once they confirm. A confirmed departure changes the code only, never this file, which stays on Google's guidance by default; a departure already in the code is the user's settled decision, so ask before reverting it.
- Do the work end to end.
- Do not undo settled decisions or weaken requirements without a real technical reason.
- Prefer root-cause fixes and the simplest architecture that fully satisfies the requirements.
- Remove dead code, obsolete compatibility layers and workarounds, unused dependencies, resources, permissions and configuration.
- Keep code clean, direct, idiomatic and maintainable.
- All code is written in English.
- Source contains no comments of any kind: no inline, header, licence, KDoc or TODO comments and no commented-out code.
- Use the latest appropriate official technologies, APIs, libraries and tools, tracking the newest published build in any release channel, including preview, alpha, beta, release candidate and canary.
- Latest applies to APIs, not only to version numbers: check every API, library, pattern and tool against what Google's official documentation recommends today and migrate whatever is deprecated, superseded or advised against, such as SharedPreferences to DataStore or AndroidViewModel to ViewModel. Do this for all code a change touches, and across the whole codebase whenever dependencies are updated, reading their release notes for deprecations and replacements.
- A newer toolchain is only acceptable while the signed release APK still installs on a release device; never compile against a preview Android platform, which stamps the package as preview-built and makes it unparseable on release builds.
- Every change that introduces a library, API, component, platform feature or tool also adds its official documentation to Official references in the same commit: Google's own pages (developer.android.com, source.android.com, android.googlesource.com, m3.material.io, fonts.google.com, developers.google.com, firebase.google.com, web.dev, developer.chrome.com) or, for a library outside Google, its maintainers' documentation, each checked to load the right page.
- Do not add a numeric product version to the visible product identity, release name, APK filename, release tag, or API User-Agent unless explicitly requested.

## Android
- The product design is strictly Google's latest Material 3 Expressive, never Material 2 or earlier Material 3: the official Compose Material 3 components inside `MaterialExpressiveTheme` with `MotionScheme.expressive()`, always the Expressive overload or variant where one exists, keeping the components' defaults, Google's rounded shapes included. The framework has no Material 3 Expressive window theme, so the platform theme is `Theme.DeviceDefault.DayNight`, never the Material Design 1 `Theme.Material`.
- Colors are Google's default Material 3 Expressive color schemes, the defaults of `MaterialExpressiveTheme`: `expressiveLightColorScheme()` in light and `darkColorScheme()` in dark. Settings has a Dynamic color switch, off by default as in Google's Now in Android sample; when it is on, every color comes from the device's `dynamicLightColorScheme` or `dynamicDarkColorScheme`, always available because the minimum SDK is 34 (Android 14). No color is hand-picked in code.
- Each screen has a single-row `TopAppBar` with the plain screen name as its title, and every list entry is its own fully rounded Material 3 Expressive `SegmentedListItem` in the surface container color of Google's samples, spaced apart rather than joined into a group.
- Frontend, UI and UX keep uniform, symmetric and consistent geometry with correct alignment, as Material 3's layout guidance defines it: each breakpoint's window margins, the top app bar's title and actions on the same edges as the content, and one alignment line for text and controls.
- Icons come from Material Symbols Rounded, except a third party's own logo where the interface names that third party.
- Text uses Google Sans from Google Fonts through Compose downloadable fonts.
- Use Google's blur and transparency effects wherever Google's guidance uses them, such as the window blur behind dialogs.
- Add an R8 keep rule only for code that a missing rule demonstrably breaks.

## Web
- Web apps follow the same Google guidance as Android: Material 3 Expressive as m3.material.io defines it. Google ships no Material 3 Expressive library for the web and Material Web is in maintenance mode, so the components are written in plain HTML, CSS and JavaScript from the official Compose Material 3 tokens, never from third-party ports.
- Colors are the Material 3 color roles that Material Color Utilities generates with `SchemeTonalSpot`, spec version 2025, from the seed `#4285F4`, written as CSS custom properties with `light-dark()`; the web has no dynamic color.
- Text uses Google Sans woff2 files from Google Fonts, served by the app itself in the latin, latin-ext and greek subsets, with the Material 3 type scale.
- Icons are Material Symbols Rounded inline SVG paths, never the Outlined or Sharp styles: unfilled (FILL 0) when not selected and filled (FILL 1) only in the selected state, as Google's navigation bar samples do; an icon whose unfilled and filled versions are identical is not used for a selectable item.
- Motion uses the Expressive spring tokens converted to CSS `linear()` easings with their settle durations.
- Below 600px the app has the navigation bar and from 600px the navigation rail; every screen has a single-row top app bar with the plain screen name, and list entries are standalone fully rounded surface-container items spaced 8px apart, as on Android.
- Input, confirmations and choices use Material dialogs, messages use snackbars, and loading uses the LoadingIndicator, contained for pull to refresh.
- The page turns off overscroll bounce so that pull to refresh behaves as Material defines it.
- Overflow actions open the Material 3 Expressive menu: groups of items in surface container low, 2px apart, with 16dp outer corners; long lists to pick from open a modal bottom sheet in surface container low with 28dp top corners, a 32×4dp drag handle and at most 640dp wide.
- Choices such as theme, language or a category open a dialog with radio buttons, never the platform's native select.
- Lists are edited in place, never through an edit mode: a leading drag handle reorders, a trailing icon button removes, and a destructive removal asks first in a dialog.
- The main action of a screen is a medium filled button (56dp), its alternative a medium tonal button; a two-state action inside a row is a filled icon button that turns from surface container highest into primary and morphs from round to 12dp corners when selected.
- Secondary information of the screen, such as elapsed time, goes in the top app bar's subtitle, and a running timer floats in the vibrant floating toolbar (primary container, 64dp, fully rounded) above the navigation bar.
- A screen's main on/off setting is the main switch bar of Android 16 Settings: at least 72dp tall, 42dp corners, always primary container with on primary container text, 32dp start and 20dp end padding, and a label that states the current state.
- Search is the Material search bar: 56dp tall, fully rounded, surface container high, a leading search icon and a trailing clear button shown only while it holds text.
- Filters are one row of filter chips that scrolls sideways; a filter with many values is a dropdown chip in the same row that shows the filter's name until a value is picked, then the value in the selected style, and picks through a radio dialog.
- A form that stays on the screen sits in a surface container panel: outlined text fields, then the actions aligned to the end, the main one filled and its alternative tonal.
- On touch phones the navigation bar hides while a text field has focus; the navigation rail never hides.

## Cloudflare
- Every recurring D1 query reads through an index, a partial index when it only looks for rows waiting for work. D1 counts every row read, rows of temporary sort tables included, against the free daily limit of 5,000,000 that all databases of the account share.
- Check every new or changed query with `EXPLAIN QUERY PLAN` and measure the `rows_read` of the D1 query API before it ships, and read the day's D1 analytics after the deploy.
- Cache values that change slowly, such as a percentile threshold, instead of recomputing them on every run.

## Verification
- While the Java target is newer than the ASM bundled with lint, the build adds `androidLintTool(platform(...))` with the newest `org.ow2.asm:asm-bom` from the version catalog; without it lint still passes but skips every class with "Unsupported class file major version".

## Official references

### Design and UI
- Material Design 3 in Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- Compose Material 3 API reference: https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary
- Get started with Jetpack Compose: https://developer.android.com/develop/ui/compose/documentation
- Material components in Compose: https://developer.android.com/develop/ui/compose/components
- Scaffold: https://developer.android.com/develop/ui/compose/components/scaffold
- Buttons in Compose: https://developer.android.com/develop/ui/compose/components/button
- Icon buttons in Compose: https://developer.android.com/develop/ui/compose/components/icon-button
- Switch in Compose: https://developer.android.com/develop/ui/compose/components/switch
- Radio button in Compose: https://developer.android.com/develop/ui/compose/components/radio-button
- Dialogs in Compose: https://developer.android.com/develop/ui/compose/components/dialog
- Snackbar in Compose: https://developer.android.com/develop/ui/compose/components/snackbar
- Progress indicators in Compose: https://developer.android.com/develop/ui/compose/components/progress
- Pull to refresh in Compose: https://developer.android.com/develop/ui/compose/components/pull-to-refresh
- App bars: https://developer.android.com/develop/ui/compose/components/app-bars
- Navigation bar in Compose: https://developer.android.com/develop/ui/compose/components/navigation-bar
- Navigation rail in Compose: https://developer.android.com/develop/ui/compose/components/navigation-rail
- Badges in Compose: https://developer.android.com/develop/ui/compose/components/badges
- Tooltips in Compose: https://developer.android.com/develop/ui/compose/components/tooltip
- State and Jetpack Compose: https://developer.android.com/develop/ui/compose/state
- Side effects in Compose: https://developer.android.com/develop/ui/compose/side-effects
- Lazy lists and grids: https://developer.android.com/develop/ui/compose/lists
- Flow layouts in Compose: https://developer.android.com/develop/ui/compose/layouts/flow
- Compose Material 3 list samples: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/ListSamples.kt
- Compose Material 3 app bar samples: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/AppBarSamples.kt
- Animations in Compose: https://developer.android.com/develop/ui/compose/animation/quick-guide
- Animation composables and AnimatedContent: https://developer.android.com/develop/ui/compose/animation/composables-modifiers#animatedcontent
- MotionScheme API reference: https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme
- Text in Compose: https://developer.android.com/develop/ui/compose/text
- Fonts and downloadable fonts in Compose: https://developer.android.com/develop/ui/compose/text/fonts
- Text fields in Compose: https://developer.android.com/develop/ui/compose/text/user-input
- OutlinedTextFieldDefaults: https://developer.android.com/reference/kotlin/androidx/compose/material3/OutlinedTextFieldDefaults
- ButtonDefaults: https://developer.android.com/reference/kotlin/androidx/compose/material3/ButtonDefaults
- ComposeMaterial3Flags: https://developer.android.com/reference/kotlin/androidx/compose/material3/ComposeMaterial3Flags
- Resources in Compose: https://developer.android.com/develop/ui/compose/resources
- LocalResources: https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/package-summary#LocalResources()
- Icons in Compose: https://developer.android.com/develop/ui/compose/graphics/images/material
- Focus in Compose: https://developer.android.com/develop/ui/compose/touch-input/focus
- Accessibility in Compose: https://developer.android.com/develop/ui/compose/accessibility
- Semantics in Compose: https://developer.android.com/develop/ui/compose/accessibility/semantics
- Settings pattern: https://developer.android.com/design/ui/mobile/guides/patterns/settings
- Adaptive apps: https://developer.android.com/develop/ui/compose/layouts/adaptive/get-started-with-adaptive-apps
- Support different display sizes: https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes
- Window size classes: https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes
- Canonical layouts: https://developer.android.com/develop/ui/compose/layouts/adaptive/canonical-layouts
- Adaptive navigation: https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation
- Navigation suite API reference: https://developer.android.com/reference/kotlin/androidx/compose/material3/adaptive/navigationsuite/package-summary
- Activity Compose API reference: https://developer.android.com/reference/kotlin/androidx/activity/compose/package-summary
- BringIntoViewSpec: https://developer.android.com/reference/kotlin/androidx/compose/foundation/gestures/BringIntoViewSpec

### Material 3 Expressive
- Building with M3 Expressive: https://m3.material.io/blog/building-with-m3-expressive
- M3 Expressive motion: https://m3.material.io/blog/m3-expressive-motion-theming
- Material components: https://m3.material.io/components
- Top app bar: https://m3.material.io/components/app-bars/overview
- Navigation bar: https://m3.material.io/components/navigation-bar/overview
- Navigation rail: https://m3.material.io/components/navigation-rail/overview
- Badges: https://m3.material.io/components/badges/overview
- Tooltips: https://m3.material.io/components/tooltips/overview
- Buttons: https://m3.material.io/components/buttons/overview
- Icon buttons: https://m3.material.io/components/icon-buttons/overview
- Lists: https://m3.material.io/components/lists/overview
- Loading indicator: https://m3.material.io/components/loading-indicator/overview
- Progress indicators: https://m3.material.io/components/progress-indicators/overview
- Dialogs: https://m3.material.io/components/dialogs/overview
- Text fields: https://m3.material.io/components/text-fields/overview
- Switch: https://m3.material.io/components/switch/overview
- Radio button: https://m3.material.io/components/radio-button/overview
- Snackbar: https://m3.material.io/components/snackbar/overview
- Menus: https://m3.material.io/components/menus/overview
- Bottom sheets: https://m3.material.io/components/bottom-sheets/overview
- Toolbars: https://m3.material.io/components/toolbars/overview
- Chips: https://m3.material.io/components/chips/overview
- Search: https://m3.material.io/components/search/overview
- Color roles: https://m3.material.io/styles/color/roles
- Dynamic color: https://m3.material.io/styles/color/dynamic/choosing-a-source
- expressiveLightColorScheme: https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#expressiveLightColorScheme()
- Compose dynamic color schemes: https://developer.android.com/develop/ui/compose/designsystems/material3#dynamic_color_schemes
- Now in Android dynamic color setting: https://raw.githubusercontent.com/android/nowinandroid/main/feature/settings/impl/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/settings/impl/SettingsDialog.kt
- Typography: https://m3.material.io/styles/typography/overview
- Typography fonts: https://m3.material.io/styles/typography/fonts
- Google Sans: https://fonts.google.com/specimen/Google+Sans
- Shape: https://m3.material.io/styles/shape/overview-principles
- Shape morph: https://m3.material.io/styles/shape/shape-morph
- Motion: https://m3.material.io/styles/motion/overview/how-it-works
- Motion transition patterns: https://m3.material.io/styles/motion/transitions/transition-patterns
- Spacing: https://m3.material.io/styles/spacing/overview
- Layout: https://m3.material.io/foundations/layout/layout-overview/overview
- Applying layout: https://m3.material.io/foundations/layout/applying-layout
- Breakpoints: https://m3.material.io/foundations/layout/breakpoints/compact
- Icons: https://m3.material.io/styles/icons/overview
- Accessibility: https://m3.material.io/foundations/overview
- Material Symbols guide: https://developers.google.com/fonts/docs/material_symbols
- Material Symbols catalogue: https://fonts.google.com/icons
- Material Symbols Android drawables: https://github.com/google/material-design-icons/tree/master/symbols/android

### Web
- Material Design 3 for Web: https://m3.material.io/develop/web
- Material Web components: https://github.com/material-components/material-web
- Material Color Utilities: https://github.com/material-foundation/material-color-utilities
- Compose Material 3 tokens: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/
- Expressive motion tokens: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ExpressiveMotionTokens.kt
- LoadingIndicator source: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/LoadingIndicator.kt
- ListItemDefaults: https://developer.android.com/reference/kotlin/androidx/compose/material3/ListItemDefaults
- SegmentedListItem: https://developer.android.com/reference/kotlin/androidx/compose/material3/SegmentedListItem.composable
- Compose Material 3 navigation bar samples: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/NavigationBarSamples.kt
- Material Symbols web SVGs: https://github.com/google/material-design-icons/tree/master/symbols/web
- Google Fonts CSS API: https://developers.google.com/fonts/docs/css2
- CSS linear() easing: https://developer.chrome.com/docs/css-ui/css-linear-easing-function
- CSS light-dark(): https://web.dev/articles/light-dark
- overscroll-behavior: https://developer.chrome.com/blog/overscroll-behavior
- Compose Material 3 search bar tokens: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/SearchBarTokens.kt
- Android Settings main switch bar: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SettingsLib/MainSwitchPreference/

### Cloudflare
- Workers Builds: https://developers.cloudflare.com/workers/ci-cd/builds/
- D1 limits: https://developers.cloudflare.com/d1/platform/limits/
- D1 pricing: https://developers.cloudflare.com/d1/platform/pricing/
- D1 metrics and analytics: https://developers.cloudflare.com/d1/observability/metrics-analytics/
- D1 indexes: https://developers.cloudflare.com/d1/best-practices/use-indexes/
- SQLite EXPLAIN QUERY PLAN: https://www.sqlite.org/eqp.html
- SQLite partial indexes: https://www.sqlite.org/partialindex.html

### Graphics and system surfaces
- Graphics in Compose: https://developer.android.com/develop/ui/compose/graphics/draw/overview
- Graphics modifiers: https://developer.android.com/develop/ui/compose/graphics/draw/modifiers
- Window blurs: https://source.android.com/docs/core/display/window-blurs
- Blur behind a window: https://developer.android.com/reference/android/view/WindowManager.LayoutParams#setBlurBehindRadius(int)
- Window background blur: https://developer.android.com/reference/android/view/Window#setBackgroundBlurRadius(int)
- RenderEffect: https://developer.android.com/reference/android/graphics/RenderEffect
- Blur in Compose: https://developer.android.com/develop/ui/compose/graphics/images/customize#blur-image
- Dialog window in Compose: https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogWindowProvider
- Shapes in Compose: https://developer.android.com/develop/ui/compose/graphics/draw/shapes
- Window insets: https://developer.android.com/develop/ui/compose/system/insets
- System bars: https://developer.android.com/develop/ui/compose/system/system-bars
- Edge to edge in Compose: https://developer.android.com/develop/ui/compose/system/setup-e2e
- Edge-to-edge design: https://developer.android.com/design/ui/mobile/guides/layout-and-content/edge-to-edge
- System bars design: https://developer.android.com/design/ui/mobile/guides/foundations/system-bars
- Predictive back in Compose: https://developer.android.com/develop/ui/compose/system/predictive-back
- Adaptive icons: https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
- System color resources: https://developer.android.com/reference/android/R.color
- Splash screens: https://developer.android.com/develop/ui/views/launch/splash-screen
- Styles and themes: https://developer.android.com/develop/ui/views/theming/themes
- Notifications: https://developer.android.com/develop/ui/compose/notifications
- Notification runtime permission: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Notification channels: https://developer.android.com/develop/ui/compose/notifications/channels
- Widgets with Jetpack Glance: https://developer.android.com/develop/ui/compose/glance
- OpenGL ES: https://developer.android.com/develop/ui/views/graphics/opengl/about-opengl
- Theme.DeviceDefault.DayNight: https://developer.android.com/reference/android/R.style#Theme_DeviceDefault_DayNight

### Platform behavior
- Permissions: https://developer.android.com/guide/topics/permissions/overview
- Package visibility: https://developer.android.com/training/package-visibility
- Intents and intent filters: https://developer.android.com/guide/components/intents-filters
- Get a result from an activity: https://developer.android.com/training/basics/intents/result
- Broadcasts: https://developer.android.com/develop/background-work/background-tasks/broadcasts
- Background tasks: https://developer.android.com/develop/background-work/background-tasks
- Task scheduling with WorkManager: https://developer.android.com/develop/background-work/background-tasks/persistent
- Foreground services: https://developer.android.com/develop/background-work/services/fgs
- Foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Doze and App Standby: https://developer.android.com/training/monitoring-device-state/doze-standby
- PowerManager: https://developer.android.com/reference/android/os/PowerManager
- Configuration changes: https://developer.android.com/guide/topics/resources/runtime-changes
- UiModeManager: https://developer.android.com/reference/android/app/UiModeManager
- PackageInstaller: https://developer.android.com/reference/android/content/pm/PackageInstaller
- Per-app language preferences: https://developer.android.com/guide/topics/resources/app-languages
- String resources: https://developer.android.com/guide/topics/resources/string-resource
- Kotlin coroutines on Android: https://developer.android.com/kotlin/coroutines
- Kotlin flows on Android: https://developer.android.com/kotlin/flow
- Kotlin serialization: https://kotlinlang.org/docs/serialization.html
- Data and file storage: https://developer.android.com/training/data-storage
- Documents and files from shared storage: https://developer.android.com/training/data-storage/shared/documents-files
- DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- Room: https://developer.android.com/training/data-storage/room
- Photo picker: https://developer.android.com/training/data-storage/shared/photo-picker
- Connect to the network: https://developer.android.com/develop/connectivity/network-ops/connecting
- VPN: https://developer.android.com/develop/connectivity/vpn
- VpnService: https://developer.android.com/reference/android/net/VpnService
- VpnService.Builder: https://developer.android.com/reference/android/net/VpnService.Builder
- HttpEngine: https://developer.android.com/reference/android/net/http/HttpEngine
- UrlRequest: https://developer.android.com/reference/android/net/http/UrlRequest
- ConnectivityManager: https://developer.android.com/reference/android/net/ConnectivityManager
- ConnectivityManager.NetworkCallback: https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback
- Read network state: https://developer.android.com/develop/connectivity/network-ops/reading-network-state
- LinkProperties: https://developer.android.com/reference/android/net/LinkProperties
- ParcelFileDescriptor: https://developer.android.com/reference/android/os/ParcelFileDescriptor
- OsConstants: https://developer.android.com/reference/android/system/OsConstants
- Intent: https://developer.android.com/reference/android/content/Intent
- Settings: https://developer.android.com/reference/android/provider/Settings
- Security checklist: https://developer.android.com/privacy-and-security/security-tips
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- Android 17 behavior changes for all apps: https://developer.android.com/about/versions/17/behavior-changes-all
- Android 17 behavior changes for apps targeting it: https://developer.android.com/about/versions/17/behavior-changes-17
- PackageManager: https://developer.android.com/reference/android/content/pm/PackageManager
- PackageInfo: https://developer.android.com/reference/android/content/pm/PackageInfo
- SigningInfo: https://developer.android.com/reference/android/content/pm/SigningInfo
- PackageInstaller.SessionParams: https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams
- PendingIntent: https://developer.android.com/reference/android/app/PendingIntent
- Manifest permissions: https://developer.android.com/reference/android/Manifest.permission
- App Bundle format: https://developer.android.com/guide/app-bundle/app-bundle-format
- bundletool: https://developer.android.com/tools/bundletool
- APK Signature Scheme v3: https://source.android.com/docs/security/features/apksigning/v3
- Background activity launches: https://developer.android.com/guide/components/activities/secure-bal
- Auto Backup: https://developer.android.com/identity/data/autobackup
- Android ABIs: https://developer.android.com/ndk/guides/abis
- Pixel densities: https://developer.android.com/training/multiscreen/screendensities
- Kotlin coroutines guide: https://kotlinlang.org/docs/coroutines-guide.html
- Coroutines best practices: https://developer.android.com/kotlin/coroutines/coroutines-best-practices
- StateFlow and SharedFlow: https://developer.android.com/kotlin/flow/stateflow-and-sharedflow

### Media and camera
- Jetpack Media3: https://developer.android.com/media/media3
- Media3 ExoPlayer: https://developer.android.com/media/media3/exoplayer
- Media3 MediaSession: https://developer.android.com/media/media3/session/control-playback
- Media3 Compose UI: https://developer.android.com/media/media3/ui/compose
- CameraX: https://developer.android.com/media/camera/camerax

### Identity
- Credential Manager: https://developer.android.com/identity/credential-manager
- Passkeys: https://developer.android.com/identity/passkeys
- Sign in with Google: https://developer.android.com/identity/sign-in/credential-manager-siwg

### Libraries and services
- OkHttp: https://lysine.dev/okhttp/
- GPlayApi: https://gitlab.com/AuroraOSS/gplayapi
- Aurora Dispenser: https://gitlab.com/AuroraOSS/aurora-dispenser
- Protocol Buffers Java Lite: https://github.com/protocolbuffers/protobuf/blob/main/java/lite.md

### Networking protocols
- DNS Queries over HTTPS (RFC 8484): https://www.rfc-editor.org/rfc/rfc8484
- Domain names (RFC 1035): https://www.rfc-editor.org/rfc/rfc1035
- Internet Protocol (RFC 791): https://www.rfc-editor.org/rfc/rfc791
- User Datagram Protocol (RFC 768): https://www.rfc-editor.org/rfc/rfc768

### Architecture, quality and testing
- Guide to app architecture: https://developer.android.com/topic/architecture
- Architecture recommendations: https://developer.android.com/topic/architecture/recommendations
- UI events: https://developer.android.com/topic/architecture/ui-layer/events
- UI state production: https://developer.android.com/topic/architecture/ui-layer/state-production
- Data layer: https://developer.android.com/topic/architecture/data-layer
- Manual dependency injection: https://developer.android.com/training/dependency-injection/manual
- ViewModel: https://developer.android.com/topic/libraries/architecture/viewmodel
- ViewModel factories: https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories
- Lifecycle in Compose: https://developer.android.com/topic/libraries/architecture/lifecycle
- Navigation 3: https://developer.android.com/guide/navigation/navigation-3
- Core app quality guidelines: https://developer.android.com/docs/quality-guidelines/core-app-quality
- Build accessible apps: https://developer.android.com/guide/topics/ui/accessibility
- Adaptive app quality guidelines: https://developer.android.com/docs/quality-guidelines/adaptive-app-quality
- App performance guide: https://developer.android.com/topic/performance/overview
- Compose performance: https://developer.android.com/develop/ui/compose/performance
- Compose performance best practices: https://developer.android.com/develop/ui/compose/performance/bestpractices
- Baseline Profiles: https://developer.android.com/topic/performance/baselineprofiles/overview
- Create Baseline Profiles: https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile
- Macrobenchmark: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- App Startup: https://developer.android.com/topic/libraries/app-startup
- Test apps on Android: https://developer.android.com/training/testing
- Local unit tests: https://developer.android.com/training/testing/local-tests
- Testing Kotlin coroutines on Android: https://developer.android.com/kotlin/coroutines/test
- Test Compose layouts: https://developer.android.com/develop/ui/compose/testing
- Compose testing APIs: https://developer.android.com/develop/ui/compose/testing/apis
- Compose Preview Screenshot Testing: https://developer.android.com/studio/preview/compose-screenshot-testing
- Robolectric strategies: https://developer.android.com/training/testing/local-tests/robolectric
- Robolectric: https://robolectric.org/
- JUnit user guide: https://docs.junit.org/current/user-guide/
- JUnit 4: https://junit.org/junit4/
- Lint: https://developer.android.com/studio/write/lint
- Lifecycle-aware coroutines: https://developer.android.com/topic/libraries/architecture/coroutines
- Compose v2 testing APIs: https://developer.android.com/develop/ui/compose/testing/migrate-v2
- Gradle JVM testing: https://docs.gradle.org/current/userguide/java_testing.html
- Robolectric compatibility: https://robolectric.org/compatibility_table/
- Robolectric shadows: https://robolectric.org/extending/
- Robolectric device configuration: https://robolectric.org/device-configuration/
- Robolectric GraphicsMode: https://robolectric.org/javadoc/latest/org/robolectric/annotation/GraphicsMode.html

### Television
- Android TV: https://developer.android.com/training/tv
- TV hardware: https://developer.android.com/training/tv/get-started/hardware
- Compose on Android TV: https://developer.android.com/training/tv/playback/compose
- Adaptive apps for TV: https://developer.android.com/develop/adaptive-apps/guides/tv/build-adaptive-apps-for-tv
- TV layouts and overscan: https://developer.android.com/design/ui/tv/guides/styles/layouts
- TV app icons and banners: https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines
- TV app quality: https://developer.android.com/docs/quality-guidelines/tv-app-quality

### Build and release
- Configure your build: https://developer.android.com/build
- Version catalogs: https://developer.android.com/build/migrate-to-catalogs
- Built-in Kotlin: https://developer.android.com/build/migrate-to-built-in-kotlin
- Java versions in Android builds: https://developer.android.com/build/jdks
- App optimization with R8: https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
- Keep rules: https://developer.android.com/topic/performance/app-optimization/keep-rules-overview
- Sign your app: https://developer.android.com/studio/publish/app-signing
- Platform and API versions: https://developer.android.com/tools/releases/platforms
- Latest Android developer updates: https://developer.android.com/latest-updates
- AndroidX releases: https://developer.android.com/jetpack/androidx/versions
- Compose releases: https://developer.android.com/jetpack/androidx/releases/compose
- Compose UI release notes: https://developer.android.com/jetpack/androidx/releases/compose-ui
- Compose Material 3 release notes: https://developer.android.com/jetpack/androidx/releases/compose-material3
- Compose Material 3 Adaptive release notes: https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
- Navigation 3 release notes: https://developer.android.com/jetpack/androidx/releases/navigation3
- Android Gradle Plugin release notes: https://developer.android.com/build/releases/gradle-plugin
- Kotlin releases: https://kotlinlang.org/docs/releases.html
- Kotlin Early Access Preview: https://kotlinlang.org/docs/eap.html
- Gradle distributions: https://services.gradle.org/distributions/
- OpenJDK builds: https://jdk.java.net/
- Android 17 QPR2: https://developer.android.com/about/versions/17/qpr2
- Version your app: https://developer.android.com/studio/publish/versioning
- Android Gradle plugin preview release notes: https://developer.android.com/build/releases/agp-preview
- Android Gradle plugin API reference: https://developer.android.com/reference/tools/gradle-api
- Compose Compiler Gradle plugin: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Compose BOM: https://developer.android.com/develop/ui/compose/bom
- Choose libraries wisely: https://developer.android.com/topic/performance/app-optimization/choose-libraries-wisely
- Fix optimization problems: https://developer.android.com/topic/performance/app-optimization/troubleshoot-the-optimization
- Google Maven repository: https://maven.google.com/web/index.html
- Activity release notes: https://developer.android.com/jetpack/androidx/releases/activity
- Lifecycle release notes: https://developer.android.com/jetpack/androidx/releases/lifecycle
- DataStore release notes: https://developer.android.com/jetpack/androidx/releases/datastore
- Media3 release notes: https://developer.android.com/jetpack/androidx/releases/media3
- CameraX release notes: https://developer.android.com/jetpack/androidx/releases/camera
- Credentials release notes: https://developer.android.com/jetpack/androidx/releases/credentials
- Benchmark release notes: https://developer.android.com/jetpack/androidx/releases/benchmark
- App Startup release notes: https://developer.android.com/jetpack/androidx/releases/startup
- ML Kit release notes: https://developers.google.com/ml-kit/release-notes
- Firebase Android SDK release notes: https://firebase.google.com/support/release-notes/android
- Compose Foundation release notes: https://developer.android.com/jetpack/androidx/releases/compose-foundation
- Gradle release notes: https://docs.gradle.org/current/release-notes.html
- Gradle Wrapper: https://docs.gradle.org/current/userguide/gradle_wrapper.html
- Gradle build environment: https://docs.gradle.org/current/userguide/build_environment.html
- Gradle configuration cache: https://docs.gradle.org/current/userguide/configuration_cache.html
- Gradle compatibility matrix: https://docs.gradle.org/current/userguide/compatibility.html
- Kotlin compiler execution strategy: https://kotlinlang.org/docs/compiler-execution-strategy.html
- Kotlin Gradle plugin compatibility: https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin
- kotlinx.coroutines releases: https://github.com/Kotlin/kotlinx.coroutines/releases
- kotlinx.serialization releases: https://github.com/Kotlin/kotlinx.serialization/releases
- OkHttp changelog: https://lysine.dev/okhttp/changelogs/changelog/
- GPlayApi changelog: https://gitlab.com/AuroraOSS/gplayapi/-/blob/master/CHANGELOG
- ASM versions: https://asm.ow2.io/versions.html
- JUnit release notes: https://docs.junit.org/current/release-notes.html
- Robolectric releases: https://github.com/robolectric/robolectric/releases
- GitHub Actions workflow syntax: https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax
- GitHub Actions runner images: https://github.com/actions/runner-images
- actions/checkout: https://github.com/actions/checkout
- actions/setup-java: https://github.com/actions/setup-java
- setup-gradle: https://github.com/gradle/actions/blob/main/docs/setup-gradle.md
- GitHub CLI releases: https://cli.github.com/manual/gh_release
- Git references REST API: https://docs.github.com/en/rest/git/refs#update-a-reference
- OpenSSL enc: https://docs.openssl.org/master/man1/openssl-enc/

### Google AI
- AI on Android: https://developer.android.com/ai
- Choose an AI solution: https://developer.android.com/ai/overview
- Gemini Nano: https://developer.android.com/ai/gemini-nano
- ML Kit GenAI APIs: https://developers.google.com/ml-kit/genai
- Gemini models on Android: https://developer.android.com/ai/gemini
- Firebase AI Logic: https://firebase.google.com/docs/ai-logic

### AI agents
- Android tools for agents: https://developer.android.com/tools/agents
- Android CLI: https://developer.android.com/tools/agents/android-cli
- Android skills: https://developer.android.com/tools/agents/android-skills
- Android skills repository: https://github.com/android/skills
