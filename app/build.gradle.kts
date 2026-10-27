import com.android.build.api.dsl.ManagedVirtualDevice.PageAlignment

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.androidx.room3)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.vasilispngs.adblock"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.vasilispngs.adblock"
        minSdk {
            version = release(34)
        }
        targetSdk {
            version = release(37)
        }
        versionCode = (System.currentTimeMillis() / 60_000 + 1_770_000_000).toInt()
        versionName = "Adblock"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseKeystore = rootProject.file("release.keystore")

    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "release"
            keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
        }
        create("nonMinifiedRelease") {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_27
        targetCompatibility = JavaVersion.VERSION_27
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        managedDevices.localDevices {
            create("pixel10Api37") {
                device = "Pixel 10"
                sdkVersion = 37
                systemImageSource = "google"
                pageAlignment = PageAlignment.FORCE_16KB_PAGES
                testedAbi = "x86_64"
            }
        }
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--enable-native-access=ALL-UNNAMED")
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.room3.paging)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    ksp(libs.androidx.room3.compiler)
    androidLintTool(platform(libs.asm.bom))
    baselineProfile(project(":baselineprofile"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.test.runner)
}
