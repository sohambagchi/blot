package app.blot.storage;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

import java.io.File;

/**
 * Stands in for the system folder picker. The app can't reach {@link TestDocumentsProvider}
 * (it requires MANAGE_DOCUMENTS), so the instrumented tests ask this unprotected provider,
 * which lives in the same package, to grant them its tree the way the picker would.
 *
 * Methods: "reset" empties the test root and turns moves on; "grant" grants the caller a
 * persistable read/write prefix grant on the tree and returns it under "uri"; "moves" with arg
 * "on" or "off" sets whether the documents provider can move documents.
 */
public class TestGrantProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        File base = TestDocumentsProvider.base(getContext());
        switch (method) {
            case "reset":
                TestDocumentsProvider.deleteRecursively(base);
                if (!base.mkdirs()) throw new IllegalStateException("cannot create " + base);
                TestDocumentsProvider.supportsMove = true;
                return null;
            case "moves":
                TestDocumentsProvider.supportsMove = "on".equals(arg);
                return null;
            case "grant":
                Uri tree = DocumentsContract.buildTreeDocumentUri(
                    TestDocumentsProvider.authority(getContext()), TestDocumentsProvider.ROOT_DOC);
                getContext().grantUriPermission(getCallingPackage(), tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                Bundle out = new Bundle();
                out.putString("uri", tree.toString());
                return out;
            default:
                throw new IllegalArgumentException(method);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
