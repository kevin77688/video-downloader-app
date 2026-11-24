package io.github.kevin77688.uvd.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import io.github.kevin77688.uvd.R
import io.github.kevin77688.uvd.core.UserException
import io.github.kevin77688.uvd.extractor.Platform
import java.io.File
import java.io.IOException

/**
 * The user-chosen SAF download folder (default: Internal storage/UniversalDownloader).
 * Images and videos from every platform go into this one folder.
 */
object MediaFolder {
    const val DEFAULT_NAME = "UniversalDownloader"
    private const val EXTERNAL_AUTHORITY = "com.android.externalstorage.documents"

    data class Entry(val uri: Uri, val name: String, val mime: String, val modified: Long, val size: Long) {
        val isVideo: Boolean get() = mime.startsWith("video/")
        val isAudio: Boolean get() = mime.startsWith("audio/")
        val platform: Platform? by lazy { FileNames.platformOf(name) }
    }

    /** Picker start location: the default folder if it already exists, otherwise storage root. */
    fun initialPickerUri(): Uri {
        val exists = File(Environment.getExternalStorageDirectory(), DEFAULT_NAME).isDirectory
        val docId = if (exists) "primary:$DEFAULT_NAME" else "primary:"
        return DocumentsContract.buildDocumentUri(EXTERNAL_AUTHORITY, docId)
    }

    fun displayPath(context: Context, tree: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return tree.toString()
        val volume = id.substringBefore(':')
        val path = id.substringAfter(':', "")
        val root = if (volume == "primary") context.getString(R.string.internal_storage) else volume
        return if (path.isEmpty()) root else "$root/$path"
    }

    fun createFile(context: Context, tree: Uri, mime: String, name: String): Uri {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return try {
            DocumentsContract.createDocument(context.contentResolver, parent, mime, name)
        } catch (e: Exception) {
            null
        } ?: throw UserException(R.string.err_folder_write)
    }

    fun list(context: Context, tree: Uri): List<Entry> {
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val out = mutableListOf<Entry>()
        context.contentResolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val mime = c.getString(2) ?: continue
                if (!mime.startsWith("image/") && !mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                out += Entry(
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)),
                    name = c.getString(1) ?: "",
                    mime = mime,
                    modified = c.getLong(3),
                    size = c.getLong(4),
                )
            }
        }
        return out.sortedWith(compareByDescending<Entry> { it.modified }.thenByDescending { it.name })
    }

    fun delete(context: Context, uri: Uri) {
        if (!DocumentsContract.deleteDocument(context.contentResolver, uri)) throw IOException("Delete failed")
    }

    /** Real filesystem path for primary-storage documents (used for media scanning). */
    fun realPath(doc: Uri): String? {
        val id = runCatching { DocumentsContract.getDocumentId(doc) }.getOrNull() ?: return null
        if (!id.startsWith("primary:")) return null
        return File(Environment.getExternalStorageDirectory(), id.removePrefix("primary:")).path
    }
}
