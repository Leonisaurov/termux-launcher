package com.termux.app;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.DialogInterface;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Dialog shown on first run to let the user choose between APT and PACMAN package managers.
 */
public class PackageManagerDialog {

    public interface PackageManagerCallback {
        void onPackageManagerSelected(String packageManager);
        void onCancel();
    }

    /**
     * Shows the package manager selection dialog.
     * @param activity The parent activity
     * @param callback Callback for the selection
     */
    public static void show(final Activity activity, final PackageManagerCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Choose Package Manager");
        builder.setMessage("Select your preferred package manager for Termux.\n\n" +
            "APT (recommended) - Debian-based, uses dpkg/apt. The standard Termux package manager.\n\n" +
            "Pacman - Arch Linux-based, uses pacman. Alternative package manager from termux-pacman.");

        builder.setPositiveButton("APT (Recommended)", (dialog, which) -> {
            dialog.dismiss();
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(activity);
            if (prefs != null) {
                prefs.setPackageManagerPreference("apt");
            }
            callback.onPackageManagerSelected("apt");
        });

        builder.setNegativeButton("Pacman", (dialog, which) -> {
            dialog.dismiss();
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(activity);
            if (prefs != null) {
                prefs.setPackageManagerPreference("pacman");
            }
            callback.onPackageManagerSelected("pacman");
        });

        builder.setCancelable(false);
        builder.show();
    }
}
