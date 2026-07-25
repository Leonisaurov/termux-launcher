package com.termux.app.fragments.settings;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceManager;
import com.termux.R;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
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
    }

    private void installXselScript() {
        try {
            String homeDir = System.getenv("HOME");
            if (homeDir == null || homeDir.isEmpty()) {
                java.io.File filesDir = requireContext().getFilesDir();
                if (filesDir != null && filesDir.getParentFile() != null) {
                    homeDir = new java.io.File(filesDir.getParentFile(), "home").getAbsolutePath();
                }
            }
            if (homeDir == null || homeDir.isEmpty()) {
                Toast.makeText(requireContext(), "Cannot determine home directory", Toast.LENGTH_SHORT).show();
                return;
            }
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
