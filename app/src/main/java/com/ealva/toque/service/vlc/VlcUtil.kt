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

package com.ealva.toque.service.vlc

import android.content.Context
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import org.videolan.libvlc.util.VLCUtil

private val LOG by lazyLogger(VlcUtil::class)

interface VlcUtil {
  val machineSpecs: VLCUtil.MachineSpecs

  companion object {
    operator fun invoke(context: Context): VlcUtil = VlcUtilImpl(context)
  }
}

private class VlcUtilImpl(context: Context) : VlcUtil {
  init {
    if (!VLCUtil.hasCompatibleCPU(context)) {
      LOG.e { +it(VLCUtil.getErrorMsg()) }
      throw makeInitException()
    }
  }

  override val machineSpecs: VLCUtil.MachineSpecs
    get() = VLCUtil.getMachineSpecs() ?: throw makeInitException()

  private fun makeInitException() =
    IllegalStateException("LibVlc init error. " + VLCUtil.getErrorMsg())
}
