plugins {
    alias(libs.plugins.compose.compiler)
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 36
    ndkVersion = "29.0.14206865"
    defaultConfig {
        applicationId = "com.tutpro.baresip"
        minSdk = 28
        targetSdk = 36
        versionCode = 465
        versionName = "74.1.0"
        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                cFlags += "-DHAVE_INTTYPES_H -lstdc++"
                arguments.addAll(listOf("-DANDROID_STL=c++_shared"))
            }
        }
        ndk {
            // noinspection ChromeOsAbiSupport
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
        vectorDrawables.useSupportLibrary = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    splits {
        abi {
            reset()
        }
    }
    buildTypes {
        debug {
            ndk {
                abiFilters.add("x86_64")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
    namespace = "com.tutpro.baresip"
}

kotlin {
    jvmToolchain(17)
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
}

dependencies {
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.compose.material3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.material)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.media)
    implementation(libs.coil.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.runtime.android)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.runtime)
}
