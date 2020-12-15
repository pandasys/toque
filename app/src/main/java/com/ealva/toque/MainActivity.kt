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

package com.ealva.toque

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Text
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.setContent
import androidx.lifecycle.lifecycleScope
import androidx.ui.tooling.preview.Preview
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.file.MediaStorage
import com.ealva.toque.scanner.MediaScannerJobIntentService
import com.ealva.toque.scanner.MediaScannerJobIntentService.Companion.Rescan
import com.ealva.toque.ui.ToqueTheme
import com.ealva.welite.db.Database
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val LOG by lazyLogger(MainActivity::class)

private const val REQUEST_READ_PERMISSION = 2000

private fun <T : AppCompatActivity> T.runWithPermission(
  permission: String,
  block: T.() -> Unit
) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
      block()
    } else {
      requestPermissions(arrayOf(READ_EXTERNAL_STORAGE), REQUEST_READ_PERMISSION)
    }
  } else {
    block()
  }
}

private suspend fun <T : AppCompatActivity> T.runSuspendWithPermission(
  permission: String,
  block: suspend T.() -> Unit
) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
      block()
    } else {
      requestPermissions(arrayOf(READ_EXTERNAL_STORAGE), REQUEST_READ_PERMISSION)
    }
  } else {
    block()
  }
}

class MainActivity : AppCompatActivity() {
  private val db: Database by inject()
  private val storage: MediaStorage by inject()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

//    getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.absolutePath?.let { toScan ->
//      runMediaScanner(listOf(toScan)) { path, uri: Uri? ->
//      }
//    }
    lifecycleScope.launchWhenStarted {
      runSuspendWithPermission(READ_EXTERNAL_STORAGE) {
        getAllAudio()
      }
    }
    setContent {
      ToqueTheme {
        Surface(color = MaterialTheme.colors.background) {
          Greeting("Android")
        }
      }
    }
  }

  private fun getAllAudio() {
    MediaScannerJobIntentService.startRescan(this, "DoIt", Rescan.ModifiedSinceLast)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    when (requestCode) {
      REQUEST_READ_PERMISSION -> {
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
          lifecycleScope.launch {
            getAllAudio()
          }
        }
      }
    }
  }
}

@Composable
fun Greeting(name: String) {
  Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
  ToqueTheme {
    Greeting("Android")
  }
}
