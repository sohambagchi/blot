package app.screenscratch.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import app.screenscratch.core.store.FileTree
import app.screenscratch.core.store.TreeEntry
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.SyncFailedException

/**
 * [FileTree] over a Storage Access Framework tree URI from `ACTION_OPEN_DOCUMENT_TREE`.
 * Refs are document URIs built against the tree, so the tree grant covers them.
 *
 * Every call is IPC to the documents provider. [list] is a single child-documents query that
 * returns name, type, size and modified time together; `DocumentFile` would cost one query per
 * getter per file.
 *
 * Throws [SecurityException] once the permission on the tree is gone.
 */
class SafFileTree(private val resolver: ContentResolver, val treeUri: Uri) : FileTree<Uri> {
    override val root: Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    override val rootKey: String = treeUri.toString()

    override fun list(dir: Uri): List<TreeEntry<Uri>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(dir))
        val cursor = resolver.query(children, PROJECTION, null, null, null)
            ?: throw FileNotFoundException("cannot list $dir")
        return cursor.use { c ->
            List(c.count) {
                c.moveToNext()
                val isDir = c.getString(2) == Document.MIME_TYPE_DIR
                TreeEntry(
                    ref = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0)),
                    name = c.getString(1),
                    isDirectory = isDir,
                    size = if (isDir || c.isNull(3)) 0L else c.getLong(3),
                    lastModified = if (c.isNull(4)) TreeEntry.UNKNOWN_TIME else c.getLong(4),
                )
            }
        }
    }

    override fun read(file: Uri): ByteArray =
        (resolver.openInputStream(file) ?: throw FileNotFoundException("cannot open $file")).use { it.readBytes() }

    override fun write(file: Uri, bytes: ByteArray) {
        // "wt": plain "w" does not truncate on some providers, leaving old bytes past the new end.
        val out = resolver.openOutputStream(file, "wt") ?: throw FileNotFoundException("cannot open $file")
        out.use {
            it.write(bytes)
            it.flush()
            try {
                (it as? FileOutputStream)?.fd?.sync()
            } catch (_: SyncFailedException) {
                // The provider handed us a pipe rather than a file; it decides durability.
            }
        }
    }

    override fun createFile(dir: Uri, name: String): Uri = create(dir, OCTET_STREAM, name)

    override fun createDirectory(dir: Uri, name: String): Uri = create(dir, Document.MIME_TYPE_DIR, name)

    override fun rename(ref: Uri, newName: String): Uri =
        DocumentsContract.renameDocument(resolver, ref, newName) ?: throw FileNotFoundException("cannot rename $ref")

    override fun delete(ref: Uri) {
        if (!DocumentsContract.deleteDocument(resolver, ref)) throw FileNotFoundException("cannot delete $ref")
    }

    // octet-stream keeps the display name exactly as given; with another MIME type the external
    // storage provider may append an extension it derives from the type.
    private fun create(dir: Uri, mime: String, name: String): Uri =
        DocumentsContract.createDocument(resolver, dir, mime, name) ?: throw FileNotFoundException("cannot create $name in $dir")

    private companion object {
        const val OCTET_STREAM = "application/octet-stream"
        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
