package com.termux.app.clipboard;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ContentProvider that exposes Android clipboard to shell scripts.
 * Access via: content://[package].clipboard/get  (query)
 *              content://[package].clipboard/set  (insert)
 *
 * Only accessible from the same app UID or shell (UID 2000/0).
 */
public class ClipboardContentProvider extends ContentProvider {

    private static final String TAG = "ClipboardProvider";
    private static final String PATH_GET = "get";
    private static final String PATH_SET = "set";

    @Override
    public boolean onCreate() {
        Log.d(TAG, "ClipboardContentProvider created, authority: " + getContext().getPackageName() + ".clipboard");
        return true;
    }

    private String getAuthority() {
        return getContext().getPackageName() + ".clipboard";
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error reading clipboard", e);
            return null;
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        try {
            enforceAccess();

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
        } catch (Exception e) {
            Log.e(TAG, "Error writing clipboard", e);
            return null;
        }
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        try {
            enforceAccess();
            ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return 0;
            ClipData clip = ClipData.newPlainText("termux", "");
            clipboard.setPrimaryClip(clip);
            return 1;
        } catch (Exception e) {
            Log.e(TAG, "Error clearing clipboard", e);
            return 0;
        }
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
        int myUid = Process.myUid();
        if (uid == myUid) return;
        if (uid == 0 || uid == 2000) return;
        Log.w(TAG, "Permission denied for UID " + uid + " (my UID: " + myUid + ")");
        throw new SecurityException("Permission denied: only the app or shell may access clipboard");
    }
}
