package com.insula.dicomviewer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Serves files from cache/exports to apps the user explicitly shares with (read-only, per-URI grants). */
public class ShareProvider extends ContentProvider {
    public static final String AUTH = "com.insula.dicomviewer.share";

    public static Uri uriFor(File f) { return Uri.parse("content://" + AUTH + "/" + Uri.encode(f.getName())); }

    File file(Uri u) {
        String name = new File(u.getLastPathSegment() == null ? "" : u.getLastPathSegment()).getName();
        return new File(new File(getContext().getCacheDir(), "exports"), name);
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(uri);
        if (!f.exists()) throw new FileNotFoundException(uri.toString());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
        File f = file(uri);
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override public String getType(Uri uri) {
        String n = uri.toString().toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg")) return "image/jpeg";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }

    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
