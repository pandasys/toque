/*
 * Copyright 2020 eAlva.com
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
}

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
    debug {
      isTestCoverageEnabled = false
    }

    release {
      isMinifyEnabled = true
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
    kotlinCompilerExtensionVersion = Libs.AndroidX.Compose.VERSION
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  lint {
    isWarningsAsErrors = false
    isAbortOnError = false
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
    languageVersion = "1.5"
    apiVersion = "1.5"
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

  implementation(Libs.AndroidX.APPCOMPAT)
  implementation(Libs.AndroidX.Ktx.CORE)
  implementation(Libs.AndroidX.Lifecycle.RUNTIME_KTX)
  implementation(Libs.AndroidX.Lifecycle.SERVICE)
  implementation(Libs.AndroidX.Lifecycle.COMMON_JAVA8)
  implementation(Libs.Datastore.PREFERENCES)

  implementation(Libs.Kotlin.Serialization.CORE)
  implementation(Libs.Kotlin.Serialization.JSON)

  implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.3.1")

  implementation(Libs.Android.MATERIAL)
  implementation(Libs.AndroidX.Compose.UI)
  implementation(Libs.AndroidX.Compose.MATERIAL)
  implementation(Libs.AndroidX.Compose.TOOLING)
  implementation(Libs.AndroidX.Activity.COMPOSE)

  implementation(Libs.LibVLC.ALL)
  implementation(Libs.PreferenceStore.STORE)
  implementation(Libs.PreferenceStore.COMPOSE)
  implementation(Libs.EAlvaLog.EALVALOG)
  implementation(Libs.EAlvaLog.CORE)
  implementation(Libs.EAlvaLog.ANDROID)
  implementation(Libs.EAlvaTag.EALVATAG)
  implementation(Libs.WeLite.CORE)
  implementation(Libs.EAlvaBrainz.BRAINZ)
  implementation(Libs.EAlvaBrainz.BRAINZ_SERVICE)

  implementation(Libs.Accompanist.GLIDE)
  implementation(Libs.Accompanist.INSETS)
  implementation(Libs.Accompanist.UI_CONTROLLER)

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
  testImplementation(Libs.AndroidX.Test.CORE) {
    exclude("junit", "junit")
  }
  testImplementation(Libs.AndroidX.Test.RULES) {
    exclude("junit", "junit")
  }
  testImplementation(Libs.Expect.EXPECT)
  testImplementation(Libs.Robolectric.ROBOLECTRIC)
  testImplementation(Libs.Kotlin.Coroutines.TEST)
//  testImplementation(ThirdParty.KOIN_TEST)

  androidTestImplementation(Libs.AndroidX.Test.Ext.JUNIT) {
    exclude("junit", "junit")
  }
  androidTestImplementation(Libs.AndroidX.Test.RUNNER) {
    exclude("junit", "junit")
  }
  androidTestImplementation(Libs.JUnit.JUNIT)
  androidTestImplementation(Libs.Expect.EXPECT)
  androidTestImplementation(Libs.Kotlin.Coroutines.TEST)
  androidTestImplementation(Libs.Koin.TEST)
}
