/*
 * Copyright (C) 2021-2022 AOSP-Krypton Project
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

package com.krypton.updater.data

import android.content.Context
import android.os.FileUtils

import androidx.documentfile.provider.DocumentFile

import dagger.hilt.android.qualifiers.ApplicationContext

import java.io.File
import java.io.FileInputStream
import java.io.IOException

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileCopier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Copies a file to export directory.
     *
     * @param inputFile the file to copy.
     * @param name the name of the moved file. Defaults to the name of [inputFile].
     * @return a [Result] indicating whether copying was successful or not.
     */
    fun copyToExportDir(inputFile: File, name: String = inputFile.name): Result<Unit> {
        val treeUriPerm =
            context.contentResolver.persistedUriPermissions.firstOrNull() ?: return Result.failure(
                IllegalStateException("Access to a directory in internal storage has not been given.")
            )
        if (!treeUriPerm.isReadPermission || !treeUriPerm.isWritePermission)
            return Result.failure(IllegalStateException("Does not have r/w permission"))
        val treeFile = DocumentFile.fromTreeUri(context, treeUriPerm.uri)
            ?: return Result.failure(Exception("Unable to open document tree"))
        treeFile.findFile(name)?.takeIf { it.isFile }?.let {
            try {
                context.contentResolver.openInputStream(it.uri)?.use { firstFileInputStream ->
                    FileInputStream(inputFile).use { secondFileInputStream ->
                        val hashVerified =
                            HashVerifier.verifyHash(firstFileInputStream, secondFileInputStream)
                        if (hashVerified) {
                            return Result.success(Unit)
                        } else {
                            it.delete()
                        }
                    }
                }
            } catch (e: IOException) {
                it.delete()
            }
        }
        val exportFile = treeFile.createFile("application/zip", name)
            ?: return Result.failure(Exception("Failed to create export file"))
        return context.contentResolver.openOutputStream(exportFile.uri)?.use { outStream ->
            FileInputStream(inputFile).use { inStream ->
                val copiedBytes = FileUtils.copy(inStream, outStream)
                if (copiedBytes != inputFile.length()) {
                    Result.failure(Exception("Failed to copy entire file"))
                } else {
                    Result.success(Unit)
                }
            }
        } ?: Result.failure(Exception("Failed to open output stream"))
    }
}