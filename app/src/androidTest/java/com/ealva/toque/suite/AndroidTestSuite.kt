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

package com.ealva.toque.suite

import com.ealva.toque.android.db.DbModuleTest
import com.ealva.toque.android.db.EqPresetAssociationDaoTest
import com.ealva.toque.android.db.EqPresetDaoTest
import com.ealva.toque.android.db.QueueStateDaoTest
import com.ealva.toque.android.file.MediaFormatTest
import com.ealva.toque.android.file.UrisTest
import com.ealva.toque.android.prefs.AppPreferencesTest
import com.ealva.toque.android.service.vlc.LibVlcFactoryTest
import com.ealva.toque.android.service.vlc.LibVlcOptionsTest
import com.ealva.toque.android.service.vlc.LibVlcPreferencesTest
import com.ealva.toque.android.service.vlc.VlcMediaTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.junit.runners.Suite

@ExperimentalCoroutinesApi
@RunWith(Suite::class)
@Suite.SuiteClasses(
  UrisTest::class,
  MediaFormatTest::class,
  AppPreferencesTest::class,
  LibVlcOptionsTest::class,
  LibVlcFactoryTest::class,
  LibVlcPreferencesTest::class,
  DbModuleTest::class,
  VlcMediaTest::class,
  EqPresetAssociationDaoTest::class,
  EqPresetDaoTest::class,
  QueueStateDaoTest::class
)
class AndroidTestSuite
