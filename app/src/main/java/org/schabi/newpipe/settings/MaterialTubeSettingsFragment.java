package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;

public final class MaterialTubeSettingsFragment extends BasePreferenceFragment {
    private static final String HALLOWEEN_KEY = "materialtube_halloween_theme";
    private static final String PRE_HALLOWEEN_ACCENT_KEY =
            "materialtube_pre_halloween_accent";
    private static final String BACKGROUND_URI_KEY = "materialtube_background_uri";
    private static final String FONT_URI_KEY = "materialtube_font_uri";
    private ActivityResultLauncher<String[]> backgroundPicker;
    private ActivityResultLauncher<String[]> fontPicker;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backgroundPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri == null) {
                        return;
                    }
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        defaultPreferences.edit().putString(BACKGROUND_URI_KEY,
                                uri.toString()).putBoolean(Constants.KEY_THEME_CHANGE, true)
                                .apply();
                        Toast.makeText(requireContext(), R.string.materialtube_background_saved,
                                Toast.LENGTH_SHORT).show();
                    } catch (final SecurityException e) {
                        Toast.makeText(requireContext(),
                                R.string.materialtube_import_failed, Toast.LENGTH_SHORT).show();
                    }
                });
        fontPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri == null) {
                        return;
                    }
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        defaultPreferences.edit().putString(FONT_URI_KEY, uri.toString())
                                .putBoolean(Constants.KEY_THEME_CHANGE, true).apply();
                        Toast.makeText(requireContext(), R.string.materialtube_font_saved,
                                Toast.LENGTH_SHORT).show();
                    } catch (final SecurityException e) {
                        Toast.makeText(requireContext(),
                                R.string.materialtube_import_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        final PreferenceScreen screen = getPreferenceManager()
                .createPreferenceScreen(requireContext());
        screen.setTitle(R.string.materialtube_settings_title);

        final PreferenceCategory network = new PreferenceCategory(requireContext());
        network.setTitle(R.string.materialtube_network_title);
        screen.addPreference(network);
        final Preference proxy = new Preference(requireContext());
        proxy.setTitle(R.string.proxy_settings_title);
        proxy.setSummary(R.string.proxy_settings_summary);
        proxy.setFragment(ProxySettingsFragment.class.getName());
        network.addPreference(proxy);

        final PreferenceCategory appearance = new PreferenceCategory(requireContext());
        appearance.setTitle(R.string.materialtube_appearance_title);
        screen.addPreference(appearance);
        final Preference colors = new Preference(requireContext());
        colors.setTitle(R.string.materialtube_accent_title);
        colors.setSummary(R.string.materialtube_accent_summary);
        colors.setFragment(AppearanceSettingsFragment.class.getName());
        appearance.addPreference(colors);

        final SwitchPreferenceCompat halloween = new SwitchPreferenceCompat(requireContext());
        halloween.setKey(HALLOWEEN_KEY);
        halloween.setTitle(R.string.materialtube_halloween_title);
        halloween.setSummary(R.string.materialtube_halloween_summary);
        halloween.setDefaultValue(false);
        halloween.setChecked(defaultPreferences.getBoolean(HALLOWEEN_KEY, false));
        halloween.setOnPreferenceChangeListener((preference, newValue) -> {
            final boolean enabled = (boolean) newValue;
            final String themeColorKey = getString(R.string.theme_color_key);
            final android.content.SharedPreferences.Editor editor = defaultPreferences.edit()
                    .putBoolean(HALLOWEEN_KEY, enabled)
                    .putBoolean(Constants.KEY_THEME_CHANGE, true);
            if (enabled) {
                editor.putString(PRE_HALLOWEEN_ACCENT_KEY,
                                defaultPreferences.getString(themeColorKey,
                                        getString(R.string.default_theme_color_value)))
                        .putString(themeColorKey, getString(R.string.theme_color_orange_value));
            } else {
                editor.putString(themeColorKey, defaultPreferences.getString(
                        PRE_HALLOWEEN_ACCENT_KEY,
                        getString(R.string.default_theme_color_value)))
                        .remove(PRE_HALLOWEEN_ACCENT_KEY);
            }
            editor.apply();
            final Activity activity = getActivity();
            if (activity != null) {
                activity.recreate();
            }
            return true;
        });
        appearance.addPreference(halloween);

        final Preference background = new Preference(requireContext());
        background.setTitle(R.string.materialtube_background_title);
        background.setSummary(R.string.materialtube_background_summary);
        background.setOnPreferenceClickListener(preference -> {
            backgroundPicker.launch(new String[]{"image/*"});
            return true;
        });
        appearance.addPreference(background);

        final Preference font = new Preference(requireContext());
        font.setTitle(R.string.materialtube_font_title);
        font.setSummary(R.string.materialtube_font_summary);
        font.setOnPreferenceClickListener(preference -> {
            fontPicker.launch(new String[]{"font/ttf", "font/otf", "application/octet-stream"});
            return true;
        });
        appearance.addPreference(font);

        final Preference avatars = new Preference(requireContext());
        avatars.setTitle(R.string.materialtube_avatar_title);
        avatars.setSummary(R.string.materialtube_avatar_summary);
        avatars.setOnPreferenceClickListener(preference -> {
            final String[] shapes = getResources().getStringArray(
                    R.array.materialtube_avatar_shape_entries);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.materialtube_avatar_title)
                    .setSingleChoiceItems(shapes,
                            defaultPreferences.getInt("materialtube_avatar_shape", 0),
                            (dialog, which) -> {
                                defaultPreferences.edit()
                                        .putInt("materialtube_avatar_shape", which)
                                        .putBoolean(Constants.KEY_THEME_CHANGE, true).apply();
                                dialog.dismiss();
                                requireActivity().recreate();
                            })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        appearance.addPreference(avatars);
        setPreferenceScreen(screen);
    }
}
