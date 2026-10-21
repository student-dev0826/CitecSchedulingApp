plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.citecschedulingapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.citecschedulingapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"https://citecscheduling.x10.mx/api/\"")
    }

    buildTypes {
        debug {
            val localApi = providers.gradleProperty("testApiBaseUrl").orNull
            if (localApi != null) {
                require(localApi.matches(Regex("http://127\\.0\\.0\\.1:[0-9]+/api/"))) {
                    "testApiBaseUrl must be a loopback HTTP URL ending in /api/"
                }
                applicationIdSuffix = ".automation"
                buildConfigField("String", "API_BASE_URL", "\"$localApi\"")
            }
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

}
