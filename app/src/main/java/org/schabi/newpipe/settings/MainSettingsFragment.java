package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.external_communication.ShareUtils;

public class MainSettingsFragment extends BasePreferenceFragment {
    public static final boolean DEBUG = MainActivity.DEBUG;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        findPreference("settings_search").setOnPreferenceClickListener(preference -> {
            ((SettingsActivity) requireActivity()).openSettingsSearch();
            return true;
        });

        findPreference("materialtube_telegram").setOnPreferenceClickListener(preference -> {
            ShareUtils.openUrlInApp(requireContext(), "https://t.me/yyoutubeyou");
            return true;
        });
        findPreference("materialtube_wallet").setOnPreferenceClickListener(preference -> {
            final ClipboardManager clipboard = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                Toast.makeText(requireContext(), R.string.general_error, Toast.LENGTH_SHORT)
                        .show();
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("MaterialTube wallet",
                        "UQCXyI90GCxJCKSe4kd32BIeLQJnuZwEzc63nNFbTG32Eefh"));
                Toast.makeText(requireContext(), R.string.materialtube_wallet_copied,
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        // Hide debug preferences in RELEASE build variant
        if (!DEBUG) {
            getPreferenceScreen().removePreference(
                    requirePreference(R.string.debug_pref_screen_key));
        }
    }

}
