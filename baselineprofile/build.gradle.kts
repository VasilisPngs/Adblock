import com.android.build.api.dsl.ManagedVirtualDevice.PageAlignment

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.vasilispngs.adblock.baselineprofile"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        minSdk {
            version = release(34)
        }
        targetSdk {
            version = release(37)
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_27
        targetCompatibility = JavaVersion.VERSION_27
    }

    targetProjectPath = ":app"

    testOptions.managedDevices.localDevices {
        create("pixel10Api37") {
            device = "Pixel 10"
            sdkVersion = 37
            systemImageSource = "google"
            pageAlignment = PageAlignment.FORCE_16KB_PAGES
            testedAbi = "x86_64"
        }
    }
}

baselineProfile {
    managedDevices += "pixel10Api37"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
