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

object Sdk {
  const val MIN_SDK_VERSION = 21
  const val TARGET_SDK_VERSION = 30
  const val COMPILE_SDK_VERSION = 30
}

object Versions {
  const val KOTLIN = "1.4.10"
  const val COMPOSE = "1.0.0-alpha04"
  const val ANDROIDX_TEST_EXT = "1.1.2"
  const val ANDROIDX_TEST = "1.3.0"
  const val MATERIAL = "1.2.1"
  const val APPCOMPAT = "1.2.0"
  const val LIFECYCLE = "2.3.0-beta01"
  const val DATASTORE = "1.0.0-alpha02"
  const val CORE_KTX = "1.3.2"
  const val ESPRESSO_CORE = "3.2.0"
  const val JUNIT = "4.13"
  const val VLC_ANDROID = "3.3.2"
  const val EALVALOG = "0.5.4"
  const val ROBOLECTRIC = "4.4"
  const val EXPECT = "1.0.1"
  const val FASTUTIL = "7.2.1"
  const val COROUTINES = "1.3.9"
  const val COROUTINES_TEST = "1.4.1"
  const val KOIN = "2.1.6"
  const val DESUGAR = "1.0.10"
}

object BuildPluginsVersion {
  const val AGP = "4.0.1"
  const val DETEKT = "1.14.1"
  const val KOTLIN = "1.4.20-RC"
  const val VERSIONS_PLUGIN = "0.33.0"
}

object SupportLibs {
  const val COMPOSE_UI = "androidx.compose.ui:ui:${Versions.COMPOSE}"
  const val COMPOSE_MATERIAL = "androidx.compose.material:material:${Versions.COMPOSE}"
  const val COMPOSE_UI_TOOLING = "androidx.ui:ui-tooling:${Versions.COMPOSE}"
  const val ANDROID_MATERIAL = "com.google.android.material:material:${Versions.MATERIAL}"
  const val ANDROIDX_APPCOMPAT = "androidx.appcompat:appcompat:${Versions.APPCOMPAT}"
  const val ANDROIDX_CORE_KTX = "androidx.core:core-ktx:${Versions.CORE_KTX}"
  const val ANDROIDX_LIFECYCLE_RUNTIME_KTX =
    "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.LIFECYCLE}"
  const val ANDROIDX_DATASTORE_PREFERENCES =
    "androidx.datastore:datastore-preferences:${Versions.DATASTORE}"
}

object ThirdParty {
  const val EALVALOG = "com.ealva:ealvalog:${Versions.EALVALOG}"
  const val EALVALOG_CORE = "com.ealva:ealvalog-core:${Versions.EALVALOG}"
  const val EALVALOG_ANDROID = "com.ealva:ealvalog-android:${Versions.EALVALOG}"
  const val VLC_ANDROID = "org.videolan.android:libvlc-all:${Versions.VLC_ANDROID}"
  const val FASTUTIL = "it.unimi.dsi:fastutil:${Versions.FASTUTIL}"
  const val COROUTINE_CORE = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES}"
  const val COROUTINE_ANDROID =
    "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.COROUTINES}"
  const val KOIN = "org.koin:koin-core:${Versions.KOIN}"
  const val KOIN_ANDROID = "org.koin:koin-android:${Versions.KOIN}"
}

object TestingLib {
  const val JUNIT = "junit:junit:${Versions.JUNIT}"
  const val ROBOLECTRIC = "org.robolectric:robolectric:${Versions.ROBOLECTRIC}"
  const val EXPECT = "com.nhaarman:expect.kt:${Versions.EXPECT}"
  const val COROUTINE_TEST =
    "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINES_TEST}"
}

object AndroidTestingLib {
  const val ANDROIDX_TEST_RULES = "androidx.test:rules:${Versions.ANDROIDX_TEST}"
  const val ANDROIDX_TEST_RUNNER = "androidx.test:runner:${Versions.ANDROIDX_TEST}"
  const val ANDROIDX_TEST_EXT_JUNIT = "androidx.test.ext:junit:${Versions.ANDROIDX_TEST_EXT}"
  const val ANDROIDX_TEST_CORE = "androidx.test:core:${Versions.ANDROIDX_TEST}"
  const val ESPRESSO_CORE = "androidx.test.espresso:espresso-core:${Versions.ESPRESSO_CORE}"
}

object ToolsLib {
  const val DESUGARING = "com.android.tools:desugar_jdk_libs:${Versions.DESUGAR}"
}
