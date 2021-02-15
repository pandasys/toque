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
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
}

android {
  compileSdkVersion(Sdk.COMPILE_SDK_VERSION)

  defaultConfig {
    minSdkVersion(Sdk.MIN_SDK_VERSION)
    targetSdkVersion(Sdk.TARGET_SDK_VERSION)

    applicationId = AppCoordinates.APP_ID
    versionCode = AppCoordinates.APP_VERSION_CODE
    versionName = AppCoordinates.APP_VERSION_NAME
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    multiDexEnabled = true

    buildConfigField("boolean", "VLC_LOGGING", "false")
    buildConfigField("String", "APP_ID", "\"${AppCoordinates.APP_ID}\"")
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  buildTypes {
    getByName("debug") {
      isTestCoverageEnabled = false
    }

    getByName("release") {
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

  packagingOptions.resources {
    excludes.addAll(
      listOf(
        "META-INF/DEPENDENCIES",
        "META-INF/LICENSE",
        "META-INF/LICENSE.txt",
        "META-INF/license.txt",
        "META-INF/NOTICE",
        "META-INF/NOTICE.txt",
        "META-INF/notice.txt",
        "META-INF/ASL2.0",
        "META-INF/AL2.0",
        "META-INF/LGPL2.1",
        "META-INF/*.kotlin_module"
      )
    )
  }

  kotlinOptions {
    jvmTarget = "1.8"
    useIR = true
    suppressWarnings = false
    verbose = true
    freeCompilerArgs = freeCompilerArgs + "-XXLanguage:+InlineClasses"
    freeCompilerArgs = freeCompilerArgs + "-Xinline-classes"
    freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
  }

  buildFeatures {
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = Versions.COMPOSE
    kotlinCompilerVersion = Versions.KOTLIN
  }
}

dependencies {
  coreLibraryDesugaring(ToolsLib.DESUGARING)
  implementation(kotlin("stdlib-jdk8"))

  implementation(SupportLibs.ANDROIDX_APPCOMPAT)
  implementation(SupportLibs.ANDROIDX_CORE_KTX)
  implementation(SupportLibs.ANDROIDX_LIFECYCLE_RUNTIME_KTX)
  implementation(SupportLibs.ANDROIDX_LIFECYCLE_SERVICE)
  implementation(SupportLibs.ANDROIDX_LIFECYCLE_COMMON)
  implementation(SupportLibs.ANDROIDX_DATASTORE_PREFERENCES)

  implementation(SupportLibs.ANDROID_MATERIAL)
  implementation(SupportLibs.COMPOSE_UI)
  implementation(SupportLibs.COMPOSE_MATERIAL)
  implementation(SupportLibs.COMPOSE_UI_TOOLING)

  implementation(ThirdParty.VLC_ANDROID)

  implementation(ThirdParty.EALVALOG)
  implementation(ThirdParty.EALVALOG_CORE)
  implementation(ThirdParty.EALVALOG_ANDROID)
  implementation(ThirdParty.EALVATAG)
  implementation(ThirdParty.WELITE_CORE)
  implementation(ThirdParty.EALVABRAINZ)
  implementation(ThirdParty.EALVABRAINZ_SERVICE)

  implementation(ThirdParty.FASTUTIL)
  implementation(ThirdParty.COROUTINE_CORE)
  implementation(ThirdParty.COROUTINE_ANDROID)

  implementation(ThirdParty.KOIN)
  implementation(ThirdParty.KOIN_ANDROID)

  implementation(ThirdParty.KOTLIN_RESULT)
  implementation(ThirdParty.KOTLIN_RESULT_CO)

  implementation(ThirdParty.PHOENIX)

  testImplementation(TestingLib.JUNIT)
  testImplementation(AndroidTestingLib.ANDROIDX_TEST_CORE) {
    exclude("junit", "junit")
  }
  testImplementation(AndroidTestingLib.ANDROIDX_TEST_RULES) {
    exclude("junit", "junit")
  }
  testImplementation(TestingLib.EXPECT)
  testImplementation(TestingLib.ROBOLECTRIC)
  testImplementation(TestingLib.COROUTINE_TEST)
  testImplementation(ThirdParty.KOIN_TEST)

  androidTestImplementation(AndroidTestingLib.ANDROIDX_TEST_ANNOTATIONS)
  androidTestImplementation(AndroidTestingLib.ANDROIDX_TEST_EXT_JUNIT) {
    exclude("junit", "junit")
  }
  androidTestImplementation(AndroidTestingLib.ANDROIDX_TEST_RUNNER) {
    exclude("junit", "junit")
  }
  androidTestImplementation(TestingLib.JUNIT)
  androidTestImplementation(TestingLib.EXPECT)
  androidTestImplementation(TestingLib.COROUTINE_TEST)
  androidTestImplementation(ThirdParty.KOIN_TEST)
}
