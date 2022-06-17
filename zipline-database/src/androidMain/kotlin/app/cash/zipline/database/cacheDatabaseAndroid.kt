/*
 * Copyright (C) 2022 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline.database

import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import okio.Path.Companion.toOkioPath

actual class DatabaseFactory(
  private val context: android.content.Context,
  private val schema: SqlDriver.Schema,
) {
  actual fun createDriver(): SqlDriver {
    return AndroidSqliteDriver(
      schema = schema,
      context = context,
      name = (context.cacheDir.toOkioPath() / "zipline" / "zipline-loader.db").toString(),
      useNoBackupDirectory = true,
    )
  }
}

actual fun isSqlException(e: Exception) = e is android.database.SQLException
