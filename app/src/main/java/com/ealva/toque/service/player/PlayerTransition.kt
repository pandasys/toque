/*
 * Copyright 2021 eAlva.com
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

package com.ealva.toque.service.player

/**
 * Implementations transition between playing and paused (or stopped) states, and vice versa. A
 * transition from paused to playing may be fade in or immediately start at full volume. From
 * playing to paused it could be fade out or immediate pause. Also, the transition needs to
 * communicate to the UI it's final state and not it's intermediate states, eg. when user presses
 * pause the UI should immediately reflect pauses, though the player may be fading out the audio for
 * some short time. Transitions also communicate their type and if a transition to another type is
 * valid. Transitions need to be cancellable as the user can toggle play/pause or rapidly switch
 * between media before a transition completes.
 */
interface PlayerTransition {
  /** Has the transition finished, either by completing or being cancelled */
  val isFinished: Boolean

  /**
   * Should the current state be considered paused. This could be a query of the real player or the
   * transition could represent a paused stated (transitioning to paused/stopped)
   */
  val isPaused: Boolean

  /**
   * Should the current state be considered playing. This could be a query of the real player or the
   * transition could represent a paused stated (transitioning to paused/stopped)
   */
  val isPlaying: Boolean

  /**
   * Has the transition been cancelled
   */
  val isCancelled: Boolean

  /**
   * Should the [nextTransition] be accepted as valid from this transition
   */
  fun accept(nextTransition: PlayerTransition): Boolean

  /**
   * Set the [TransitionPlayer] this transition should control
   */
  fun setPlayer(transitionPlayer: TransitionPlayer)

  /**
   * Execute the transition
   */
  suspend fun execute()

  /**
   * Notify this transition that it should "wrap it up" ASAP
   */
  fun setCancelled()

  /**
   * [Type] represents the final state of the transition if it runs to completion
   */
  val type: Type

  /**
   * Represents the type of transition - the final player state if the transition runs to
   * completion.
   */
  sealed class Type {
    /** Should the transition respond as paused */
    abstract val isPaused: Boolean

    /** Should the transition respond as playing */
    abstract val isPlaying: Boolean

    /** Can this Type transition to [next] */
    abstract fun canTransitionTo(next: Type): Boolean

    object Play : Type() {
      override val isPaused: Boolean = false
      override val isPlaying: Boolean = true
      override fun canTransitionTo(next: Type): Boolean = when (next) {
        Play, NoOp -> false
        Pause, Shutdown -> true
      }
    }

    object Pause : Type() {
      override val isPaused: Boolean = true
      override val isPlaying: Boolean = false
      override fun canTransitionTo(next: Type): Boolean = when (next) {
        Pause, NoOp -> false
        Play, Shutdown -> true
      }
    }

    object Shutdown : Type() {
      override val isPaused: Boolean = true
      override val isPlaying: Boolean = false
      override fun canTransitionTo(next: Type): Boolean = false
    }

    object NoOp : Type() {
      override val isPaused: Boolean = true
      override val isPlaying: Boolean = false
      override fun canTransitionTo(next: Type): Boolean = true
    }
  }
}
