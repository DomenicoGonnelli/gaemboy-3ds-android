// Copyright 2026 GAEMBOY
// Licensed under GPLv2 or any later version
// Refer to the misc/licenses/gplv2.txt file included.

package org.citra.citra_emu.gaemboy

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * Exposes the app-private user directory through SAF so the emulator core (which only speaks
 * content:// trees) works without asking the user to pick a folder.
 */
class GaemboyStorageProvider : DocumentsProvider() {
    private val rootDir: File get() = GaemboyStorage.rootDir(context!!)

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, GaemboyStorage.ROOT_ID)
            add(Root.COLUMN_DOCUMENT_ID, GaemboyStorage.ROOT_ID)
            add(Root.COLUMN_TITLE, "GAEMBOY 3DS")
            add(Root.COLUMN_ICON, context!!.applicationInfo.icon)
            add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
            add(Root.COLUMN_AVAILABLE_BYTES, rootDir.freeSpace)
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        addRow(cursor, documentId, fileFor(documentId))
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        fileFor(parentDocumentId).listFiles()?.forEach {
            addRow(cursor, childId(parentDocumentId, it.name), it)
        }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileFor(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val parent = fileFor(parentDocumentId)
        var name = displayName
        var file = File(parent, name)
        var suffix = 1
        while (file.exists()) {
            name = "$displayName ($suffix)"
            file = File(parent, name)
            suffix++
        }
        val ok = if (mimeType == Document.MIME_TYPE_DIR) file.mkdirs() else file.createNewFile()
        if (!ok) throw FileNotFoundException("Cannot create $name")
        return childId(parentDocumentId, name)
    }

    override fun deleteDocument(documentId: String) {
        if (!fileFor(documentId).deleteRecursively()) {
            throw FileNotFoundException("Cannot delete $documentId")
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = fileFor(documentId)
        if (!file.renameTo(File(file.parentFile, displayName))) {
            throw FileNotFoundException("Cannot rename $documentId")
        }
        return childId(documentId.substringBeforeLast('/'), displayName)
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String {
        val file = fileFor(sourceDocumentId)
        if (!file.renameTo(File(fileFor(targetParentDocumentId), file.name))) {
            throw FileNotFoundException("Cannot move $sourceDocumentId")
        }
        return childId(targetParentDocumentId, file.name)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId.startsWith("$parentDocumentId/")

    override fun getDocumentType(documentId: String): String = mimeOf(fileFor(documentId))

    private fun fileFor(documentId: String): File {
        val root = rootDir
        if (documentId == GaemboyStorage.ROOT_ID) return root
        if (!documentId.startsWith(GaemboyStorage.ROOT_ID + "/")) {
            throw FileNotFoundException("Unknown document $documentId")
        }
        val file = File(root, documentId.substring(GaemboyStorage.ROOT_ID.length + 1))
        if (!file.canonicalPath.startsWith(root.canonicalPath)) {
            throw FileNotFoundException("Invalid document $documentId")
        }
        return file
    }

    private fun childId(parentId: String, name: String) = "$parentId/$name"

    private fun mimeOf(file: File): String {
        if (file.isDirectory) return Document.MIME_TYPE_DIR
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
    }

    private fun addRow(cursor: MatrixCursor, documentId: String, file: File) {
        if (!file.exists()) throw FileNotFoundException("Missing document $documentId")
        val base = Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or
            Document.FLAG_SUPPORTS_MOVE
        val flags = if (file.isDirectory) {
            base or Document.FLAG_DIR_SUPPORTS_CREATE
        } else {
            base or Document.FLAG_SUPPORTS_WRITE
        }
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, documentId)
            add(
                Document.COLUMN_DISPLAY_NAME,
                if (documentId == GaemboyStorage.ROOT_ID) "GAEMBOY 3DS" else file.name
            )
            add(Document.COLUMN_SIZE, if (file.isDirectory) 0L else file.length())
            add(Document.COLUMN_MIME_TYPE, mimeOf(file))
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(Document.COLUMN_FLAGS, flags)
        }
    }

    companion object {
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
            Root.COLUMN_ICON, Root.COLUMN_FLAGS, Root.COLUMN_AVAILABLE_BYTES
        )
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS
        )
    }
}
