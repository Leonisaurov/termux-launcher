package com.termux.app.clipboard;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ClipboardContentProvider extends ContentProvider {

    private static final String AUTHORITY = "com.termux.app.clipboard";
    private static final String PATH_GET = "get";
    private static final String PATH_SET = "set";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        enforceAccess();

        ClipboardManager clipboard = (ClipboardManager) getContext()
            .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return null;

        String text = "";
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            ClipData.Item item = clip.getItemAt(0);
            CharSequence cs = item.getText();
            if (cs != null) text = cs.toString();
        }

        MatrixCursor cursor = new MatrixCursor(new String[]{"value"});
        cursor.addRow(new Object[]{text});
        return cursor;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        enforceAccess();

        if (!PATH_SET.equals(uri.getLastPathSegment())) return null;

        ClipboardManager clipboard = (ClipboardManager) getContext()
            .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return null;

        String text = "";
        if (values != null && values.containsKey("text")) {
            text = values.getAsString("text");
        }

        ClipData clip = ClipData.newPlainText("termux", text != null ? text : "");
        clipboard.setPrimaryClip(clip);
        return uri;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        enforceAccess();
        ClipboardManager clipboard = (ClipboardManager) getContext()
            .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return 0;
        ClipData clip = ClipData.newPlainText("termux", "");
        clipboard.setPrimaryClip(clip);
        return 1;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return "text/plain";
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        return insert(uri, values) != null ? 1 : 0;
    }

    private void enforceAccess() {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return;
        if (uid == 0 || uid == 2000) return;
        throw new SecurityException("Permission denied: only the app or shell may access clipboard");
    }
}
