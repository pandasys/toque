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

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
  id("com.android.application") version PluginsVersion.AGP apply false
  kotlin("android") version PluginsVersion.KOTLIN apply false
  kotlin("plugin.serialization") version PluginsVersion.SERIALIZATION apply false
  kotlin("kapt") version PluginsVersion.SERIALIZATION apply false
  id("io.gitlab.arturbosch.detekt") version PluginsVersion.DETEKT
  id("com.github.ben-manes.versions") version PluginsVersion.VERSIONS
  id("org.jetbrains.dokka") version PluginsVersion.DOKKA
  id("com.vanniktech.maven.publish") version PluginsVersion.PUBLISH
}

allprojects {
  repositories {
    maven { setUrl("https://jitpack.io") }
    google()
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
  }
}

subprojects {
  apply {
    plugin("io.gitlab.arturbosch.detekt")
  }

  detekt {
    config = rootProject.files("config/detekt/detekt.yml")
  }
}

buildscript {
  repositories {
    google()
    mavenCentral()
  }
  dependencies {
    classpath(Libs.AGP)
    classpath(Libs.Kotlin.KGP)
  }
}

tasks.withType<DependencyUpdatesTask> {
  rejectVersionIf {
    isNonStable(candidate.version)
  }
  checkForGradleUpdate = true
}

fun isNonStable(version: String) = "^[0-9,.v-]+(-r)?$".toRegex().matches(version).not()
