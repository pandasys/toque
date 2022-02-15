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

object SdkVersion {
  const val COMPILE = 31
  const val MIN = 23
  const val TARGET = 30
}

object PluginsVersion {
  const val AGP = "7.1.1"
  const val DETEKT = "1.19.0"
  const val DOKKA = "1.6.10"
  const val KOTLIN = "1.6.10"
  const val PUBLISH = "0.18.0"
  const val SERIALIZATION = "1.6.10"
  const val VERSIONS = "0.41.0"
}

object Libs {
  const val AGP = "com.android.tools.build:gradle:${PluginsVersion.AGP}"
  const val DESUGAR = "com.android.tools:desugar_jdk_libs:1.1.5"

  object Accompanist {
    private const val VERSION = "0.20.3"
    const val INSETS = "com.google.accompanist:accompanist-insets:$VERSION"
    const val UI_CONTROLLER = "com.google.accompanist:accompanist-systemuicontroller:$VERSION"
    const val PERMISSIONS = "com.google.accompanist:accompanist-permissions:$VERSION"
    const val PAGER = "com.google.accompanist:accompanist-pager:$VERSION"
  }

  object Android {
    const val MATERIAL = "com.google.android.material:material:1.5.0"
  }

  object AndroidX {
    const val APPCOMPAT = "androidx.appcompat:appcompat:1.4.1"
    //const val PALETTE = "androidx.palette:palette:1.0.0"

    object Ktx {
      const val CORE = "androidx.core:core-ktx:1.7.0"
    }

    object Activity {
      const val COMPOSE = "androidx.activity:activity-compose:1.4.0"
    }

    object Constraint {
      const val LAYOUT_COMPOSE = "androidx.constraintlayout:constraintlayout-compose:1.0.0-beta02"
    }

    object Compose {
      @Suppress("MemberVisibilityCanBePrivate")
      const val COMPILER_VERSION = "1.1.0-rc02"
      private const val VERSION = "1.1.0-rc01"

      //const val FOUNDATION = "androidx.compose.foundation:foundation:$VERSION"
      const val UI = "androidx.compose.ui:ui:$VERSION"
      const val MATERIAL = "androidx.compose.material:material:$VERSION"
      const val TOOLING = "androidx.compose.ui:ui-tooling:$VERSION"

//      const val RUNTIME = "androidx.compose.runtime:runtime:$VERSION"
//      const val LAYOUT = "androidx.compose.foundation:foundation-layout:${VERSION}"
//      const val MATERIAL_ICONS_EXTENDED =
//        "androidx.compose.material:material-icons-extended:${VERSION}"
    }

    const val EXIF = "androidx.exifinterface:exifinterface:1.3.3"

    object Lifecycle {
      private const val VERSION = "2.4.0"
      const val RUNTIME_KTX = "androidx.lifecycle:lifecycle-runtime-ktx:$VERSION"
      const val COMMON_JAVA8 = "androidx.lifecycle:lifecycle-common-java8:$VERSION"
      const val SERVICE = "androidx.lifecycle:lifecycle-service:$VERSION"
      const val EXTS = "androidx.lifecycle:lifecycle-extensions:2.2.0"
//    const val VIEW_MODEL_COMPOSE = "androidx.lifecycle:lifecycle-viewmodel-compose:1.0.0-alpha05"
//    const val VIEW_MODEL_KTX = "androidx.lifecycle:lifecycle-viewmodel-ktx:$VERSION"
    }

    object Test {
      private const val VERSION = "1.4.0"
      const val CORE = "androidx.test:core:$VERSION"
      const val RULES = "androidx.test:rules:$VERSION"
      const val RUNNER = "androidx.test:runner:$VERSION"

      object Ext {
        private const val VERSION = "1.1.3"
        const val JUNIT = "androidx.test.ext:junit-ktx:$VERSION"
      }

      // const val ESPRESSO_CORE = "androidx.test.espresso:espresso-core:3.2.0"
    }

    object WorkManager {
      const val KTX_RUNTIME = "androidx.work:work-runtime-ktx:2.7.1"
    }
  }

  object Coil {
    const val COIL = "io.coil-kt:coil:1.4.0"
    const val COIL_COMPOSE = "io.coil-kt:coil-compose:1.4.0"
  }

  object ComposeReorder {
    const val REORDERABLE = "org.burnoutcrew.composereorderable:reorderable:0.7.4"
  }

  object Datastore {
    const val PREFERENCES = "androidx.datastore:datastore-preferences:1.0.0"
  }

  object EAlvaBrainz {
    private const val VERSION = "0.10.2-0"
    const val BRAINZ = "com.ealva:ealvabrainz:$VERSION"
    const val BRAINZ_SERVICE = "com.ealva:ealvabrainz-service:$VERSION"
  }

  object EAlvaLog {
    private const val VERSION = "0.5.6-SNAPSHOT"
    const val EALVALOG = "com.ealva:ealvalog:$VERSION"
    const val ANDROID = "com.ealva:ealvalog-android:$VERSION"
    const val CORE = "com.ealva:ealvalog-core:$VERSION"
  }

  object EAlvaMusicInfo {
    private const val VERSION = "0.2.11-0"
    const val MUSIC_INFO = "com.ealva:musicinfo:$VERSION"
  }

  object EAlvaTag {
    const val EALVATAG = "com.ealva:ealvatag:0.4.7-SNAPSHOT"
  }

  object Expect {
    const val EXPECT = "com.nhaarman:expect.kt:1.0.1"
  }

  object FastUtil {
    const val FASTUTIL = "it.unimi.dsi:fastutil:7.2.1"
  }

  const val GUAVA = "com.google.guava:guava:31.0.1-android"

  object Gowtham {
    const val RATING_BAR = "com.github.a914-gowtham:compose-ratingbar:1.2.1"
  }

  object JUnit {
    private const val VERSION = "4.12"
    const val JUNIT = "junit:junit:$VERSION"
  }

  object Koin {
    private const val VERSION = "3.1.5"
    private const val COMPOSE_VERSION = "3.1.5"
    const val CORE = "io.insert-koin:koin-core:$VERSION"
    const val ANDROID = "io.insert-koin:koin-android:$VERSION"
    const val COMPOSE = "io.insert-koin:koin-androidx-compose:$COMPOSE_VERSION"
    const val ANDROID_EXT = "io.insert-koin:koin-android-ext:3.0.2"
    const val EXT = "io.insert-koin:koin-core-ext:3.0.2"
    const val TEST = "io.insert-koin:koin-test:$VERSION"
  }

  object Kotlin {
    private const val VERSION = "1.6.10"
    const val KGP = "org.jetbrains.kotlin:kotlin-gradle-plugin:$VERSION"
//    const val REFLECT = "org.jetbrains.kotlin:kotlin-reflect:$VERSION"

    // const val STDLIB = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$VERSION"
    // const val EXTENSIONS = "org.jetbrains.kotlin:kotlin-android-extensions:$VERSION"

    object Coroutines {
      private const val VERSION = "1.6.0"
      const val CORE = "org.jetbrains.kotlinx:kotlinx-coroutines-core:$VERSION"
      const val ANDROID =
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:$VERSION"
      const val TEST = "org.jetbrains.kotlinx:kotlinx-coroutines-test:$VERSION"
    }

    object Serialization {
      private const val VERSION = "1.3.2"
      const val CORE = "org.jetbrains.kotlinx:kotlinx-serialization-core:$VERSION"
      const val JSON = "org.jetbrains.kotlinx:kotlinx-serialization-json:$VERSION"
    }
  }

  object LibVLC {
    const val ALL = "org.videolan.android:libvlc-all:3.4.8"
  }

  object NumberPicker {
    const val COMPOSE = "com.chargemap.compose:numberpicker:0.0.11"
  }

  object Phoenix {
    const val PHOENIX = "com.jakewharton:process-phoenix:2.1.2"
  }

  object PreferenceStore {
    private const val VERSION = "0.10.13-0"
    const val STORE = "com.ealva:pref-store:$VERSION"
    const val COMPOSE = "com.ealva:compose-preference:$VERSION"
  }

  object Result {
    private const val VERSION = "1.1.14"
    const val RESULT = "com.michael-bull.kotlin-result:kotlin-result:$VERSION"
    const val COROUTINES = "com.michael-bull.kotlin-result:kotlin-result-coroutines:$VERSION"
  }

  object Robolectric {
    const val ROBOLECTRIC = "org.robolectric:robolectric:4.7.3"
  }

  object SimpleStack {
    const val CORE = "com.github.Zhuinden:simple-stack:2.6.2"
    const val EXT = "com.github.Zhuinden:simple-stack-extensions:2.2.2"
    const val COMPOSE = "com.github.Zhuinden:simple-stack-compose-integration:0.9.4"
  }

  object Square {
    const val BOM = "com.squareup.okhttp3:okhttp-bom:4.9.3"
    const val OKIO = "com.squareup.okio:okio"
    const val LOGGING_INTERCEPTOR = "com.squareup.okhttp3:logging-interceptor"
  }

  object WeLite {
    const val CORE = "com.ealva:welite-core:0.5.0-SNAPSHOT"
  }
}
