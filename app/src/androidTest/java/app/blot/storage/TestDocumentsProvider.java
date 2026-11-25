package app.blot.storage;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * A documents provider over a directory in the test APK's own files, so SAF code can be tested
 * end to end without driving the system picker. It runs in the test APK's process, where the
 * app's Kotlin runtime isn't loaded, hence Java.
 *
 * Document ids are "root" and "root/<relative path>". Like the real external-storage provider
 * it reports directory modified times; unlike it, it refuses to create or rename onto an
 * existing name instead of picking "name (1)", so store logic that would collide fails loudly.
 * It moves documents, flagging them FLAG_SUPPORTS_MOVE, until {@link #supportsMove} is turned off
 * to test the store's copy fallback.
 */
public class TestDocumentsProvider extends DocumentsProvider {
    /** Named after the test package, so the debug and shrunk test APKs can be installed together. */
    static String authority(android.content.Context context) {
        return context.getPackageName() + ".documents";
    }
    static final String ROOT_DOC = "root";

    /** Set through {@link TestGrantProvider}'s "moves" method; this runs in the same process. */
    static volatile boolean supportsMove = true;

    private static final String[] ROOT_COLUMNS = {
        Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS,
    };
    private static final String[] DOC_COLUMNS = {
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS,
    };

    static File base(android.content.Context context) {
        return new File(context.getFilesDir(), "saf-root");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File fileFor(String docId) throws FileNotFoundException {
        if (docId.equals(ROOT_DOC)) return base(getContext());
        if (!docId.startsWith(ROOT_DOC + "/") || docId.contains("/../") || docId.endsWith("/..")) {
            throw new FileNotFoundException("bad document id " + docId);
        }
        return new File(base(getContext()), docId.substring(ROOT_DOC.length() + 1));
    }

    private static String childId(String parentId, String name) {
        return parentId + "/" + name;
    }

    private static void addRow(MatrixCursor cursor, String docId, File f) {
        boolean dir = f.isDirectory();
        int flags = Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_RENAME
            | (dir ? Document.FLAG_DIR_SUPPORTS_CREATE : Document.FLAG_SUPPORTS_WRITE)
            | (supportsMove ? Document.FLAG_SUPPORTS_MOVE : 0);
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, docId)
            .add(Document.COLUMN_DISPLAY_NAME, docId.equals(ROOT_DOC) ? "Test root" : f.getName())
            .add(Document.COLUMN_MIME_TYPE, dir ? Document.MIME_TYPE_DIR : "application/octet-stream")
            .add(Document.COLUMN_SIZE, dir ? null : f.length())
            .add(Document.COLUMN_LAST_MODIFIED, f.lastModified())
            .add(Document.COLUMN_FLAGS, flags);
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor c = new MatrixCursor(projection != null ? projection : ROOT_COLUMNS);
        c.newRow()
            .add(Root.COLUMN_ROOT_ID, "test")
            .add(Root.COLUMN_DOCUMENT_ID, ROOT_DOC)
            .add(Root.COLUMN_TITLE, "Blot test")
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD);
        return c;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        File f = fileFor(documentId);
        if (!f.exists()) throw new FileNotFoundException(documentId);
        MatrixCursor c = new MatrixCursor(projection != null ? projection : DOC_COLUMNS);
        addRow(c, documentId, f);
        return c;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
        throws FileNotFoundException {
        File[] children = fileFor(parentDocumentId).listFiles();
        if (children == null) throw new FileNotFoundException(parentDocumentId);
        MatrixCursor c = new MatrixCursor(projection != null ? projection : DOC_COLUMNS);
        for (File f : children) addRow(c, childId(parentDocumentId, f.getName()), f);
        return c;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
        throws FileNotFoundException {
        File f = fileFor(documentId);
        if (!f.isFile()) throw new FileNotFoundException(documentId);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
        throws FileNotFoundException {
        File f = new File(fileFor(parentDocumentId), displayName);
        if (f.exists()) throw new FileNotFoundException("exists: " + f);
        boolean ok;
        try {
            ok = Document.MIME_TYPE_DIR.equals(mimeType) ? f.mkdir() : f.createNewFile();
        } catch (IOException e) {
            ok = false;
        }
        if (!ok) throw new FileNotFoundException("cannot create " + f);
        return childId(parentDocumentId, displayName);
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        File f = fileFor(documentId);
        File target = new File(f.getParentFile(), displayName);
        if (!f.exists() || target.exists() || !f.renameTo(target)) {
            throw new FileNotFoundException("cannot rename " + documentId + " to " + displayName);
        }
        return childId(documentId.substring(0, documentId.lastIndexOf('/')), displayName);
    }

    @Override
    public String moveDocument(String sourceDocumentId, String sourceParentDocumentId, String targetParentDocumentId)
        throws FileNotFoundException {
        if (!supportsMove) throw new UnsupportedOperationException("move is turned off");
        File f = fileFor(sourceDocumentId);
        File target = new File(fileFor(targetParentDocumentId), f.getName());
        if (!f.getParentFile().equals(fileFor(sourceParentDocumentId)) || !f.exists() || target.exists() || !f.renameTo(target)) {
            throw new FileNotFoundException("cannot move " + sourceDocumentId + " to " + targetParentDocumentId);
        }
        return childId(targetParentDocumentId, f.getName());
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        File f = fileFor(documentId);
        if (!f.exists() || !deleteRecursively(f)) throw new FileNotFoundException("cannot delete " + documentId);
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return documentId.startsWith(parentDocumentId + "/");
    }

    static boolean deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursively(c);
        }
        return f.delete();
    }
}
