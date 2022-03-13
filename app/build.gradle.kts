/*
 * Copyright 2021 Eric A. Snell
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
  id("com.android.application")
  kotlin("android")
  kotlin("plugin.serialization")
  id("kotlin-parcelize")
  kotlin("kapt")
}

val localProperties = com.android.build.gradle.internal.cxx.configure.gradleLocalProperties(rootDir)
val musicInfoAppName: String = localProperties.getProperty("MUSICINFO_APP_NAME", "\"\"")
val musicInfoAppVersion: String = localProperties.getProperty("MUSICINFO_APP_VERSION", "\"\"")
val musicInfoEmail: String = localProperties.getProperty("MUSICINFO_CONTACT_EMAIL", "\"\"")
val lastFmApiKey: String = localProperties.getProperty("LASTFM_API_KEY", "\"\"")
val spotifyClientId: String = localProperties.getProperty("SPOTIFY_CLIENT_ID", "\"\"")
val spotifyClientSecret: String = localProperties.getProperty("SPOTIFY_CLIENT_SECRET", "\"\"")

android {
  compileSdk = SdkVersion.COMPILE

  defaultConfig {
    minSdk = SdkVersion.MIN
    targetSdk = SdkVersion.TARGET

    applicationId = AppVersion.ID
    versionCode = AppVersion.VERSION_CODE
    versionName = AppVersion.VERSION_NAME
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    signingConfig = signingConfigs.getByName("debug")
  }

  buildTypes {
    all {
      buildConfigField("String", "MUSICINFO_APP_NAME", musicInfoAppName)
      buildConfigField("String", "MUSICINFO_APP_VERSION", musicInfoAppVersion)
      buildConfigField("String", "MUSICINFO_CONTACT_EMAIL", musicInfoEmail)
      buildConfigField("String", "LASTFM_API_KEY", lastFmApiKey)
      buildConfigField("String", "SPOTIFY_CLIENT_ID", spotifyClientId)
      buildConfigField("String", "SPOTIFY_CLIENT_SECRET", spotifyClientSecret)
    }

    debug {
      isTestCoverageEnabled = false
    }

    release {
      signingConfig = signingConfigs["debug"]
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  sourceSets {
    val sharedTestDir = File("src/sharedTest/java")
    getByName("test").java.srcDirs(sharedTestDir)
    getByName("androidTest").java.srcDirs(sharedTestDir)

    val resDir = File("sampledata")
    getByName("test").resources.srcDirs(resDir)
    getByName("androidTest").resources.srcDirs(resDir)
  }

  buildFeatures {
    // Enables Jetpack Compose for this module
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = Libs.AndroidX.Compose.COMPILER_VERSION
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  lint {
    warningsAsErrors = false
    abortOnError = false
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }

  packagingOptions {
    resources {
      excludes += listOf(
        "META-INF/AL2.0",
        "META-INF/LGPL2.1"
      )
    }
  }

  kotlinOptions {
    jvmTarget = "1.8"
    languageVersion = "1.6"
    apiVersion = "1.6"
    suppressWarnings = false
    verbose = true
    freeCompilerArgs = listOf(
      "-Xopt-in=kotlin.RequiresOptIn",
      "-Xskip-prerelease-check"
    )
  }
}

dependencies {
  coreLibraryDesugaring(Libs.DESUGAR)
  implementation(kotlin("stdlib-jdk8"))

  implementation(Libs.NumberPicker.COMPOSE)

  implementation(Libs.AndroidX.APPCOMPAT)
  implementation(Libs.AndroidX.EXIF)
  implementation(Libs.AndroidX.Activity.COMPOSE)
  implementation(Libs.AndroidX.Constraint.LAYOUT_COMPOSE)
  implementation(Libs.AndroidX.Ktx.CORE)
  implementation(Libs.AndroidX.Lifecycle.RUNTIME_KTX)
  implementation(Libs.AndroidX.Lifecycle.SERVICE)
  implementation(Libs.AndroidX.Lifecycle.COMMON_JAVA8)
  implementation(Libs.AndroidX.Lifecycle.EXTS)
  implementation(Libs.AndroidX.WorkManager.KTX_RUNTIME)

  implementation(Libs.AndroidX.Compose.UI)
  implementation(Libs.AndroidX.Compose.MATERIAL)
  implementation(Libs.AndroidX.Compose.TOOLING)

  implementation(Libs.Datastore.PREFERENCES)

  implementation(Libs.Kotlin.Serialization.CORE)
  implementation(Libs.Kotlin.Serialization.JSON)
  implementation(Libs.Kotlin.DATETIME)

  implementation(Libs.Android.MATERIAL)

  implementation(Libs.ComposeReorder.REORDERABLE)

  implementation(Libs.LibVLC.ALL)

  implementation(Libs.PreferenceStore.STORE)
  implementation(Libs.PreferenceStore.COMPOSE)
  implementation(Libs.EAlvaLog.EALVALOG)
  implementation(Libs.EAlvaLog.CORE)
  implementation(Libs.EAlvaLog.ANDROID)
  implementation(Libs.EAlvaTag.EALVATAG)
  implementation(Libs.EAlvaMusicInfo.MUSIC_INFO)
  implementation(Libs.EAlvaBrainz.BRAINZ)
  implementation(Libs.EAlvaBrainz.BRAINZ_SERVICE)
  implementation(Libs.WeLite.CORE)
  implementation(Libs.WeLite.KTIME)

  implementation(Libs.Coil.COIL)
  implementation(Libs.Coil.COIL_COMPOSE)

  implementation(Libs.Accompanist.INSETS)
  implementation(Libs.Accompanist.UI_CONTROLLER)
  implementation(Libs.Accompanist.PERMISSIONS)
  implementation(Libs.Accompanist.PAGER)

  implementation(Libs.Gowtham.RATING_BAR)

  implementation(platform(Libs.Square.BOM))
  implementation(Libs.Square.OKIO)
  implementation(Libs.Square.LOGGING_INTERCEPTOR)

  implementation(Libs.FastUtil.FASTUTIL)
  implementation(Libs.Kotlin.Coroutines.CORE)
  implementation(Libs.Kotlin.Coroutines.ANDROID)

  implementation(Libs.Koin.CORE)
  implementation(Libs.Koin.EXT)
  implementation(Libs.Koin.ANDROID)
  implementation(Libs.Koin.ANDROID_EXT)
  implementation(Libs.Koin.COMPOSE)

  implementation(Libs.SimpleStack.EXT)
  implementation(Libs.SimpleStack.COMPOSE)

  implementation(Libs.Result.RESULT)
  implementation(Libs.Result.COROUTINES)

  implementation(Libs.Phoenix.PHOENIX)

  testImplementation(Libs.JUnit.JUNIT)
  testImplementation(Libs.AndroidX.Test.CORE)
  testImplementation(Libs.AndroidX.Test.Ext.JUNIT)
  testImplementation(Libs.AndroidX.Test.RULES)
  testImplementation(Libs.Expect.EXPECT)
  testImplementation(Libs.Robolectric.ROBOLECTRIC)
  testImplementation(Libs.Kotlin.Coroutines.TEST)
//  testImplementation(ThirdParty.KOIN_TEST)

  androidTestImplementation(Libs.AndroidX.Test.Ext.JUNIT)
  androidTestImplementation(Libs.AndroidX.Test.RUNNER)
  androidTestImplementation(Libs.JUnit.JUNIT)
  androidTestImplementation(Libs.Expect.EXPECT)
  androidTestImplementation(Libs.Kotlin.Coroutines.TEST)
  androidTestImplementation(Libs.Koin.TEST)

  implementation("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")
}
