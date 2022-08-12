/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.flamingo.updater.data.util

import android.system.ErrnoException
import android.system.Os
import android.util.Slog

import java.io.File

private const val TAG = "FilePermissionHelper"

/**
 * Check whether a file has rwx perms
 *
 * @param file the file to check perms for.
 * @return true if file has rwx perms.
 */
fun checkRWX(file: File): Boolean = file.canRead() && file.canWrite() && file.canExecute()

/**
 * Set owner and mode of of given path. This method is
 * from android.os.FileUtils class, stripped down to
 * just what we require.
 *
 * @param file the file to change permissions.
 * @param mode to apply through `chmod`
 * @return 0 on success, otherwise errno.
 */
fun setPermissions(file: File, mode: Int): Int {
    return try {
        Os.chmod(file.absolutePath, mode)
        0
    } catch (e: ErrnoException) {
        Slog.w(TAG, "Failed to chmod ${file.absolutePath}, ${e.message}")
        e.errno
    }
}