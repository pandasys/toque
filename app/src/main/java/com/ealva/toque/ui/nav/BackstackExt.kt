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

package com.ealva.toque.ui.nav

import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.toque.navigation.ComposeKey
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.History
import com.zhuinden.simplestack.StateChange

private val LOG by lazyLogger("BackstackExt")

inline val Backstack.topScreen: ComposeKey get() = top()

private inline fun History<ComposeKey>.ifSameRun(
  history: History<ComposeKey>,
  command: () -> Unit
) {
  if (this == history) command() else LOG.w(RuntimeException()) { it("History changed") }
}

private fun Backstack.navigateIfAllowed(command: () -> Unit) {
  val currentHistory: History<ComposeKey> = getHistory()
  try {
    topScreen.navigateIfAllowed {
      currentHistory.ifSameRun(getHistory()) {
        command()
      }
    }
  } catch (e: java.lang.IllegalStateException) {
    // if no topScreen, just execute
    command()
  }
}

fun Backstack.goToAboveRoot(screen: ComposeKey) =
  navigateIfAllowed { setHistory(listOf(root(), screen), StateChange.REPLACE) }

fun Backstack.goToScreen(screen: ComposeKey) = navigateIfAllowed { goTo(screen) }

fun Backstack.back() = navigateIfAllowed { goBack() }

fun Backstack.setScreenHistory(newHistory: List<ComposeKey>, direction: Int) =
  navigateIfAllowed() { setHistory(newHistory, direction) }

fun Backstack.goToRootScreen() = navigateIfAllowed() { jumpToRoot() }
