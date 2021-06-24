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

object SdkVersion {
  const val COMPILE = 30
  const val MIN = 21
  const val TARGET = 30
}

object PluginsVersion {
  const val AGP = "7.0.0-beta04"
  const val DETEKT = "1.17.1"
  const val DOKKA = "1.4.32"
  const val KOTLIN = "1.5.10"
  const val PUBLISH = "0.16.0"
  const val SERIALIZATION = "1.5.10"
  const val VERSIONS = "0.39.0"
}

object Libs {
  const val AGP = "com.android.tools.build:gradle:${PluginsVersion.AGP}"
  const val DESUGAR = "com.android.tools:desugar_jdk_libs:1.1.5"

  object Accompanist {
    private const val VERSION = "0.12.0"
    const val GLIDE = "com.google.accompanist:accompanist-glide:$VERSION"
    const val INSETS = "com.google.accompanist:accompanist-insets:$VERSION"
    const val UI_CONTROLLER = "com.google.accompanist:accompanist-systemuicontroller:$VERSION"
  }

  object Android {
    const val MATERIAL = "com.google.android.material:material:1.3.0"
  }

  object AndroidX {
    const val APPCOMPAT = "androidx.appcompat:appcompat:1.3.0"
    const val PALETTE = "androidx.palette:palette:1.0.0"

    object Ktx {
      const val CORE = "androidx.core:core-ktx:1.6.0-alpha03"
    }

    object Activity {
      const val COMPOSE = "androidx.activity:activity-compose:1.3.0-alpha08"
    }

    object Constraint {
      const val LAYOUT_COMPOSE = "androidx.constraintlayout:constraintlayout-compose:1.0.0-alpha07"
    }

    object Compose {
      const val VERSION = "1.0.0-beta09"
      const val FOUNDATION = "androidx.compose.foundation:foundation:$VERSION"
      const val UI = "androidx.compose.ui:ui:$VERSION"
      const val MATERIAL = "androidx.compose.material:material:$VERSION"
      const val TOOLING = "androidx.compose.ui:ui-tooling:$VERSION"

//      const val RUNTIME = "androidx.compose.runtime:runtime:$VERSION"
//      const val LAYOUT = "androidx.compose.foundation:foundation-layout:${VERSION}"
//      const val MATERIAL_ICONS_EXTENDED =
//        "androidx.compose.material:material-icons-extended:${VERSION}"
    }

    object Lifecycle {
      private const val VERSION = "2.4.0-alpha01"
      const val RUNTIME_KTX = "androidx.lifecycle:lifecycle-runtime-ktx:$VERSION"
      const val COMMON_JAVA8 = "androidx.lifecycle:lifecycle-common-java8:$VERSION"
      const val SERVICE = "androidx.lifecycle:lifecycle-service:$VERSION"
//    const val VIEW_MODEL_COMPOSE = "androidx.lifecycle:lifecycle-viewmodel-compose:1.0.0-alpha05"
//    const val VIEW_MODEL_KTX = "androidx.lifecycle:lifecycle-viewmodel-ktx:$VERSION"
    }

    object Test {
      private const val VERSION = "1.4.0-alpha04"
      const val CORE = "androidx.test:core:$VERSION"
      const val RULES = "androidx.test:rules:$VERSION"
      const val RUNNER = "androidx.test:runner:$VERSION"

      object Ext {
        private const val VERSION = "1.1.3-alpha04"
        const val JUNIT = "androidx.test.ext:junit-ktx:$VERSION"
      }

      // const val ESPRESSO_CORE = "androidx.test.espresso:espresso-core:3.2.0"
    }
  }

  object Datastore {
    const val PREFERENCES = "androidx.datastore:datastore-preferences:1.0.0-beta01"
  }

  object EAlvaBrainz {
    private const val VERSION = "0.7.0-SNAPSHOT"
    const val BRAINZ = "com.ealva:ealvabrainz:$VERSION"
    const val BRAINZ_SERVICE = "com.ealva:ealvabrainz-service:$VERSION"
  }

  object EAlvaLog {
    private const val VERSION = "0.5.6-SNAPSHOT"
    const val EALVALOG = "com.ealva:ealvalog:$VERSION"
    const val ANDROID = "com.ealva:ealvalog-android:$VERSION"
    const val CORE = "com.ealva:ealvalog-core:$VERSION"
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

  object JUnit {
    private const val VERSION = "4.13.2"
    const val JUNIT = "junit:junit:$VERSION"
  }

  object Koin {
    private const val VERSION = "3.0.2"
    private const val COMPOSE_VERSION = "3.0.1"
    const val COMPOSE = "io.insert-koin:koin-androidx-compose:$COMPOSE_VERSION"
    const val CORE = "io.insert-koin:koin-core:$VERSION"
    const val ANDROID = "io.insert-koin:koin-android:$VERSION"
    const val ANDROID_EXT = "io.insert-koin:koin-android-ext:$VERSION"
    const val EXT = "io.insert-koin:koin-core-ext:$VERSION"
    const val TEST = "io.insert-koin:koin-test:$VERSION"
  }

  object Kotlin {
    private const val VERSION = "1.5.10"
    const val KGP = "org.jetbrains.kotlin:kotlin-gradle-plugin:$VERSION"

    // const val STDLIB = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$VERSION"
    // const val EXTENSIONS = "org.jetbrains.kotlin:kotlin-android-extensions:$VERSION"

    object Coroutines {
      private const val VERSION = "1.5.0"
      const val CORE = "org.jetbrains.kotlinx:kotlinx-coroutines-core:$VERSION"
      const val ANDROID =
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:$VERSION"
      const val TEST = "org.jetbrains.kotlinx:kotlinx-coroutines-test:$VERSION"
    }

    object Serialization {
      private const val VERSION = "1.2.1"
      const val CORE = "org.jetbrains.kotlinx:kotlinx-serialization-core:$VERSION"
      const val JSON = "org.jetbrains.kotlinx:kotlinx-serialization-json:$VERSION"
    }
  }

  object LibVLC {
    const val ALL = "org.videolan.android:libvlc-all:3.3.14"
  }

  object Navigation {
    const val COMPOSE = "androidx.navigation:navigation-compose:2.4.0-alpha01"
  }

  object Phoenix {
    const val PHOENIX = "com.jakewharton:process-phoenix:2.0.0"
  }

  object PreferenceStore {
    private const val VERSION = "0.7.6-SNAPSHOT"
    const val STORE = "com.ealva:preference-store:$VERSION"
    const val COMPOSE = "com.ealva:compose-preference:$VERSION"
  }

  object Result {
    private const val VERSION = "1.1.12"
    const val RESULT = "com.michael-bull.kotlin-result:kotlin-result:$VERSION"
    const val COROUTINES = "com.michael-bull.kotlin-result:kotlin-result-coroutines:$VERSION"
  }

  object Robolectric {
    const val ROBOLECTRIC = "org.robolectric:robolectric:4.5.1"
  }

  object SimpleStack {
    const val CORE = "com.github.Zhuinden:simple-stack:2.6.2"
    const val EXT = "com.github.Zhuinden:simple-stack-extensions:2.2.2"
    const val COMPOSE = "com.github.Zhuinden:simple-stack-compose-integration:0.4.2"
  }

  object WeLite {
    const val CORE = "com.ealva:welite-core:0.1.4-SNAPSHOT"
  }
}
