// Copyright 2026 GAEMBOY
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

package org.citra.citra_emu.gaemboy

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.preference.PreferenceManager
import java.io.File
import org.citra.citra_emu.features.settings.model.CitraSettings
import org.citra.citra_emu.utils.PermissionsHandler

/**
 * Zero-setup storage: unless the user picked a folder, the emulator uses an app-private
 * directory exposed by [GaemboyStorageProvider], so a game launched by intent starts directly.
 */
object GaemboyStorage {
    const val ROOT_ID = "root"

    fun authority(context: Context) = "${context.packageName}.user"

    fun rootDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "user").apply { mkdirs() }
    }

    fun treeUri(context: Context): Uri =
        DocumentsContract.buildTreeDocumentUri(authority(context), ROOT_ID)

    fun isOwnUri(context: Context, uri: Uri) = uri.authority == authority(context)

    fun nativePath(context: Context, uri: Uri): String {
        val documentId = uri.lastPathSegment ?: ROOT_ID
        val root = rootDir(context).absolutePath
        return if (documentId == ROOT_ID) root else root + documentId.removePrefix(ROOT_ID)
    }

    fun ensureDefaultDirectory(context: Context) {
        if (PermissionsHandler.citraDirectory.toString().isNotEmpty()) return
        rootDir(context)
        PermissionsHandler.setCitraDirectory(treeUri(context).toString())
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(CitraSettings.PREF_FIRST_APP_LAUNCH, false)
            .apply()
    }

    /**
     * Fills unset config.ini keys with GAEMBOY defaults. OpenGL ES is preferred because several
     * Adreno 6xx Vulkan drivers crash in vkCreateGraphicsPipelines.
     */
    fun applyConfigDefaults(context: Context) {
        val config = File(rootDir(context), "config/config.ini")
        if (!config.isFile) return
        val lines = config.readLines()
        var changed = false
        val updated = lines.map { line ->
            val key = line.substringBefore('=').trim()
            val value = CONFIG_DEFAULTS[key]
            if (value != null && line.contains('=') && line.substringAfter('=').isBlank()) {
                changed = true
                "$key = $value"
            } else {
                line
            }
        }
        if (changed) config.writeText(updated.joinToString("\n", postfix = "\n"))
    }

    private val CONFIG_DEFAULTS = mapOf("graphics_api" to "1")
}
