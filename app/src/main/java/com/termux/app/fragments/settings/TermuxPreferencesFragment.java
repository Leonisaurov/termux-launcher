package com.termux.app.fragments.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;
import com.termux.R;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.pkgconv.PackageManagerConverter;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

@Keep
public class TermuxPreferencesFragment extends MaterialPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxPreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.termux_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);

        Preference installXsel = findPreference("install_xsel");
        if (installXsel != null) {
            installXsel.setOnPreferenceClickListener(pref -> {
                installXselScript();
                return true;
            });
        }

        Preference packageManager = findPreference("package_manager");
        if (packageManager != null) {
            packageManager.setOnPreferenceClickListener(pref -> {
                Context ctx = getContext();
                if (ctx == null) return false;

                final TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(ctx, true);
                if (prefs == null) return false;
                String currentPM = prefs.getPackageManagerPreference();
                if (currentPM == null) currentPM = "apt";

                String targetPM = "apt".equals(currentPM) ? "pacman" : "apt";
                String targetName = "pacman".equals(targetPM) ? "Pacman" : "APT";

                new AlertDialog.Builder(ctx)
                    .setTitle("Switch Package Manager")
                    .setMessage("Switch from " + currentPM.toUpperCase()
                        + " to " + targetName + "?\n\n"
                        + "This will convert your installed packages database and swap package manager binaries.\n"
                        + "Your home directory will NOT be affected.\n\n"
                        + "A backup will be created before the conversion.")
                    .setPositiveButton("Switch to " + targetName, (dialog, which) -> {
                        new Thread(() -> {
                            try {
                                PackageManagerConverter converter = new PackageManagerConverter();
                                converter.setProgressCallback(new PackageManagerConverter.ProgressCallback() {
                                    @Override
                                    public void onProgress(String message, int percent) {
                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                pref.setSummary(message + " (" + percent + "%)");
                                            });
                                        }
                                    }
                                    @Override
                                    public void onError(String message, Exception e) {
                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                new AlertDialog.Builder(getActivity())
                                                    .setTitle("Conversion Failed")
                                                    .setMessage(message + "\n\nRollback has been attempted.")
                                                    .setPositiveButton("OK", null)
                                                    .show();
                                            });
                                        }
                                    }
                                    @Override
                                    public void onComplete(boolean success) {
                                        if (getActivity() != null && success) {
                                            getActivity().runOnUiThread(() -> {
                                                prefs.setPackageManagerPreference(targetPM);
                                                pref.setSummary("Current: " + targetPM);
                                            });
                                        }
                                    }
                                });
                                PackageManagerConverter.PackageManager target =
                                    "pacman".equals(targetPM) ?
                                    PackageManagerConverter.PackageManager.PACMAN :
                                    PackageManagerConverter.PackageManager.APT;
                                converter.convert(target);
                            } catch (Exception e) {
                                android.util.Log.e("TermuxPreferencesFragment", "PM conversion failed", e);
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        try {
                                            Toast.makeText(getActivity(),
                                                "Conversion failed: " + e.getMessage(),
                                                Toast.LENGTH_LONG).show();
                                        } catch (Exception ignored) {}
                                    });
                                }
                            }
                        }).start();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                return true;
            });
            // Update summary with current value
            Preference pmPref = findPreference("package_manager");
            if (pmPref != null) {
                TermuxAppSharedPreferences currentPrefs = TermuxAppSharedPreferences.build(getContext(), true);
                if (currentPrefs != null) {
                    String current = currentPrefs.getPackageManagerPreference();
                    if (current != null) {
                        pmPref.setSummary("Current: " + current);
                    }
                }
            }
        }
    }

    private void installXselScript() {
        try {
            // Use TermuxConstants for the most reliable home directory path
            String homeDir = TermuxConstants.TERMUX_HOME_DIR_PATH;

            File localBin = new File(homeDir, ".local/bin");
            if (!localBin.exists()) {
                localBin.mkdirs();
            }

            InputStream is = requireContext().getAssets().open("bin/xsel");
            File destFile = new File(localBin, "xsel");

            FileOutputStream os = new FileOutputStream(destFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.close();
            is.close();

            destFile.setExecutable(true, true);

            Toast.makeText(requireContext(),
                "xsel installed to " + destFile.getAbsolutePath(),
                Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                "Error installing xsel: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }
}

class TermuxPreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;

    private final TermuxAppSharedPreferences mPreferences;

    private static TermuxPreferencesDataStore mInstance;

    private TermuxPreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized TermuxPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TermuxPreferencesDataStore(context);
        }
        return mInstance;
    }
}
