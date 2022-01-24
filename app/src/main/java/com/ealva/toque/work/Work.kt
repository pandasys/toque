/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.work

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerFactory
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.BuildConfig
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

private val LOG by lazyLogger(Work::class)

interface Work {
  fun getWorkManagerConfiguration(): Configuration
  fun enqueue(request: WorkRequest): Operation
  fun addFactory(workerClassName: String, factory: WorkerFactory)

  companion object {
    operator fun invoke(appContext: Context): Work = WorkImpl(appContext)
  }
}

private class WorkImpl(private val appContext: Context) : Work {
  private val workManager: WorkManager
    get() = WorkManager.getInstance(appContext)

  private val delegatingWorkerFactory = DelegatingWorkerFactory()

  private val factories = Object2ObjectOpenHashMap<String, WorkerFactory>()

  override fun addFactory(workerClassName: String, factory: WorkerFactory) = when {
    !factories.containsKey(workerClassName) -> delegatingWorkerFactory.addFactory(factory)
    else -> LOG.e { it("Attempt to add WorkerFactory for %s more than once", workerClassName) }
  }

  override fun getWorkManagerConfiguration(): Configuration = (if (BuildConfig.DEBUG) {
    Configuration.Builder()
      .setMinimumLoggingLevel(Log.DEBUG)
  } else {
    Configuration.Builder()
      .setMinimumLoggingLevel(Log.ERROR)
  })
    .setJobSchedulerJobIdRange(1, Int.MAX_VALUE)
    .setWorkerFactory(delegatingWorkerFactory)
    .build()

  override fun enqueue(request: WorkRequest): Operation = workManager.enqueue(request)
}
