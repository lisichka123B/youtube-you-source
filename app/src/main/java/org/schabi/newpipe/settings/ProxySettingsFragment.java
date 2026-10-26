package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.network.AppProxySelector;
import org.schabi.newpipe.network.ProxyCredentialStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ProxySettingsFragment extends BasePreferenceFragment {
    private static final String CUSTOM_PROXY_LIST_KEY = "materialtube_custom_proxy_list";
    private static final int CONNECT_TIMEOUT_MILLIS = 1_500;
    private static final int MAX_PARALLEL_CHECKS = 8;

    private EditTextPreference hostPreference;
    private EditTextPreference portPreference;
    private EditTextPreference passwordPreference;
    private ProxyCredentialStore credentialStore;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        final ListPreference modePreference = requirePreference(R.string.proxy_mode_key);
        hostPreference = requirePreference(R.string.proxy_host_key);
        portPreference = requirePreference(R.string.proxy_port_key);
        passwordPreference = requirePreference(R.string.proxy_password_key);
        credentialStore = new ProxyCredentialStore(requireContext());

        hostPreference.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_URI);
            editText.setSingleLine(true);
        });
        portPreference.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            editText.setSingleLine(true);
        });
        passwordPreference.setPersistent(false);
        passwordPreference.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            editText.setSingleLine(true);
        });
        passwordPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            final String password = String.valueOf(newValue);
            final boolean saved = password.isEmpty()
                    ? credentialStore.clearPassword()
                    : credentialStore.savePassword(password);
            if (!saved) {
                Toast.makeText(requireContext(), R.string.proxy_password_save_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            passwordPreference.setText(null);
            updatePasswordSummary();
            AppProxySelector.refreshInstalled();
            return false;
        });
        updatePasswordSummary();

        hostPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!isValidHost(String.valueOf(newValue))) {
                Toast.makeText(requireContext(), R.string.proxy_invalid_host,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        });
        portPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!isValidPort(String.valueOf(newValue))) {
                Toast.makeText(requireContext(), R.string.proxy_invalid_port,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        });
        modePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!getString(R.string.proxy_mode_disabled_value).equals(newValue)
                    && !isValidHost(hostPreference.getText())) {
                Toast.makeText(requireContext(), R.string.proxy_invalid_host,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            if (!getString(R.string.proxy_mode_disabled_value).equals(newValue)
                    && !isValidPort(portPreference.getText())) {
                Toast.makeText(requireContext(), R.string.proxy_invalid_port,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        });

        findPreference("wifi_proxy_catalog").setOnPreferenceClickListener(preference -> {
            checkProxyCatalog("WifiProxy.txt", R.string.wifi_proxy_catalog_title);
            return true;
        });
        findPreference("lte_proxy_catalog").setOnPreferenceClickListener(preference -> {
            checkProxyCatalog("LTEPROXY.txt", R.string.lte_proxy_catalog_title);
            return true;
        });
        findPreference("custom_proxy_link").setOnPreferenceClickListener(preference -> {
            showCustomProxyLinkDialog();
            return true;
        });
        findPreference("custom_proxy_catalog").setOnPreferenceClickListener(preference -> {
            final List<ProxyEndpoint> endpoints = readSavedCustomProxies();
            if (endpoints.isEmpty()) {
                Toast.makeText(requireContext(), R.string.proxy_custom_empty,
                        Toast.LENGTH_SHORT).show();
            } else {
                checkProxyCatalog(endpoints, R.string.proxy_custom_catalog_title);
            }
            return true;
        });
    }

    private void checkProxyCatalog(@NonNull final String assetName,
                                   final int titleResource) {
        final List<ProxyEndpoint> endpoints;
        try {
            endpoints = readProxyCatalog(assetName);
        } catch (final IOException e) {
            Toast.makeText(requireContext(), R.string.proxy_check_failed,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        checkProxyCatalog(endpoints, titleResource);
    }

    private void checkProxyCatalog(@NonNull final List<ProxyEndpoint> endpoints,
                                   final int titleResource) {
        if (endpoints.isEmpty()) {
            Toast.makeText(requireContext(), R.string.proxy_custom_empty,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), R.string.proxy_checking, Toast.LENGTH_SHORT).show();
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final String title = getString(titleResource);
        new Thread(() -> {
            final List<ProxyEndpoint> results;
            try {
                results = measureConnectionTimes(endpoints);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            mainHandler.post(() -> {
                if (isAdded()) {
                    showProxyResults(title, results);
                }
            });
        }, "proxy-connectivity-check").start();
    }

    private void showCustomProxyLinkDialog() {
        final EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://example.org/proxies.txt");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.proxy_custom_link_title)
                .setMessage(R.string.proxy_custom_link_summary)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    final String value = input.getText().toString().trim();
                    if (!isHttpsLink(value)) {
                        Toast.makeText(requireContext(), R.string.proxy_custom_link_invalid,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    importProxyLink(value);
                })
                .show();
    }

    private void showCustomProxyEntriesDialog() {
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(8);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setText(defaultPreferences.getString(CUSTOM_PROXY_LIST_KEY, ""));
        final androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.proxy_custom_manual_title)
                .setMessage(R.string.proxy_custom_manual_prompt)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    final List<ProxyEndpoint> endpoints = parseCustomProxyEntries(
                            input.getText().toString());
                    if (endpoints == null) {
                        return;
                    }
                    final StringBuilder content = new StringBuilder();
                    for (final ProxyEndpoint endpoint : endpoints) {
                        if (content.length() > 0) {
                            content.append('\n');
                        }
                        content.append(endpoint.address());
                    }
                    defaultPreferences.edit()
                            .putString(CUSTOM_PROXY_LIST_KEY, content.toString())
                            .apply();
                    if (endpoints.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.proxy_custom_empty,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        checkProxyCatalog(endpoints, R.string.proxy_custom_catalog_title);
                    }
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private List<ProxyEndpoint> parseCustomProxyEntries(@NonNull final String content) {
        final Map<String, ProxyEndpoint> endpoints = new LinkedHashMap<>();
        final String[] lines = content.split("\\R");
        for (int lineNumber = 0; lineNumber < lines.length; lineNumber++) {
            final String line = lines[lineNumber].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            for (final String entry : line.split("[,\\s]+")) {
                final int separator = entry.lastIndexOf(':');
                if (separator <= 0 || !isValidHost(entry.substring(0, separator))
                        || !isValidPort(entry.substring(separator + 1))) {
                    Toast.makeText(requireContext(),
                            getString(R.string.proxy_custom_manual_invalid, lineNumber + 1),
                            Toast.LENGTH_LONG).show();
                    return null;
                }
                final ProxyEndpoint endpoint = new ProxyEndpoint(
                        entry.substring(0, separator),
                        Integer.parseInt(entry.substring(separator + 1)));
                endpoints.putIfAbsent(endpoint.address(), endpoint);
            }
        }
        return new ArrayList<>(endpoints.values());
    }

    private boolean isHttpsLink(@NonNull final String value) {
        final Uri uri = Uri.parse(value);
        final String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                && !host.isBlank() && uri.getUserInfo() == null;
    }

    private void importProxyLink(@NonNull final String link) {
        Toast.makeText(requireContext(), R.string.proxy_custom_importing,
                Toast.LENGTH_SHORT).show();
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            final List<ProxyEndpoint> imported;
            try {
                imported = downloadProxyList(link);
            } catch (final IOException e) {
                mainHandler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), R.string.proxy_custom_import_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                final java.util.LinkedHashSet<String> addresses =
                        new java.util.LinkedHashSet<>();
                for (final ProxyEndpoint endpoint : readSavedCustomProxies()) {
                    addresses.add(endpoint.address());
                }
                for (final ProxyEndpoint endpoint : imported) {
                    addresses.add(endpoint.address());
                }
                defaultPreferences.edit()
                        .putString(CUSTOM_PROXY_LIST_KEY, String.join("\n", addresses))
                        .apply();
                Toast.makeText(requireContext(),
                        getString(R.string.proxy_custom_imported, imported.size()),
                        Toast.LENGTH_LONG).show();
                checkProxyCatalog(readSavedCustomProxies(),
                        R.string.proxy_custom_catalog_title);
            });
        }, "proxy-list-import").start();
    }

    @NonNull
    private List<ProxyEndpoint> downloadProxyList(@NonNull final String link)
            throws IOException {
        final HttpURLConnection connection =
                (HttpURLConnection) new URL(link).openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(8_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "text/plain");
        try {
            final int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("Proxy list request returned HTTP " + responseCode);
            }
            final List<ProxyEndpoint> endpoints = new ArrayList<>();
            try (java.io.InputStream input = connection.getInputStream();
                 java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                final byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 1_048_576) {
                        throw new IOException("Proxy list exceeds 1 MiB");
                    }
                    output.write(buffer, 0, count);
                }
                final String content = output.toString(StandardCharsets.UTF_8.name());
                for (final String rawLine : content.split("\\R")) {
                    final String line = rawLine.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    final int separator = line.lastIndexOf(':');
                    if (separator <= 0 || !isValidHost(line.substring(0, separator))
                            || !isValidPort(line.substring(separator + 1))) {
                        continue;
                    }
                    endpoints.add(new ProxyEndpoint(line.substring(0, separator),
                            Integer.parseInt(line.substring(separator + 1))));
                }
            }
            if (endpoints.isEmpty()) {
                throw new IOException("Proxy list contains no valid host:port entries");
            }
            return endpoints;
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    private List<ProxyEndpoint> readSavedCustomProxies() {
        final Map<String, ProxyEndpoint> endpoints = new LinkedHashMap<>();
        final String content = defaultPreferences.getString(CUSTOM_PROXY_LIST_KEY, "");
        if (content == null || content.isBlank()) {
            return new ArrayList<>();
        }
        for (final String line : content.split("\\R")) {
            final int separator = line.lastIndexOf(':');
            if (separator > 0 && isValidHost(line.substring(0, separator))
                    && isValidPort(line.substring(separator + 1))) {
                final ProxyEndpoint endpoint = new ProxyEndpoint(line.substring(0, separator),
                        Integer.parseInt(line.substring(separator + 1)));
                endpoints.putIfAbsent(endpoint.address(), endpoint);
            }
        }
        return new ArrayList<>(endpoints.values());
    }

    @NonNull
    private List<ProxyEndpoint> readProxyCatalog(@NonNull final String assetName)
            throws IOException {
        final List<ProxyEndpoint> endpoints = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                requireContext().getAssets().open(assetName), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                final int separator = value.lastIndexOf(':');
                if (separator <= 0 || !isValidPort(value.substring(separator + 1))) {
                    throw new IOException("Invalid proxy entry in " + assetName);
                }
                endpoints.add(new ProxyEndpoint(value.substring(0, separator),
                        Integer.parseInt(value.substring(separator + 1))));
            }
        }
        return endpoints;
    }

    @NonNull
    private List<ProxyEndpoint> measureConnectionTimes(
            @NonNull final List<ProxyEndpoint> endpoints) throws InterruptedException {
        final ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(MAX_PARALLEL_CHECKS, endpoints.size()));
        try {
            final List<Callable<ProxyEndpoint>> checks = new ArrayList<>();
            for (final ProxyEndpoint endpoint : endpoints) {
                checks.add(() -> {
                    final long startedAt = System.nanoTime();
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(endpoint.host, endpoint.port),
                                CONNECT_TIMEOUT_MILLIS);
                        return endpoint.withLatency(
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                    } catch (final IOException e) {
                        return endpoint.unreachable();
                    }
                });
            }
            final List<Future<ProxyEndpoint>> futures =
                    executor.invokeAll(checks, 8, TimeUnit.SECONDS);
            final List<ProxyEndpoint> results = new ArrayList<>(futures.size());
            for (final Future<ProxyEndpoint> future : futures) {
                try {
                    results.add(future.get());
                } catch (final CancellationException
                               | java.util.concurrent.ExecutionException e) {
                    results.add(endpoints.get(results.size()).unreachable());
                }
            }
            results.sort(Comparator.comparing((ProxyEndpoint endpoint) -> !endpoint.reachable)
                    .thenComparingLong(endpoint -> endpoint.latencyMillis));
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private void showProxyResults(@NonNull final String title,
                                  @NonNull final List<ProxyEndpoint> endpoints) {
        final String[] entries = new String[endpoints.size()];
        for (int i = 0; i < endpoints.size(); i++) {
            final ProxyEndpoint endpoint = endpoints.get(i);
            entries[i] = endpoint.reachable
                    ? getString(R.string.proxy_latency_result, endpoint.address(),
                    endpoint.latencyMillis)
                    : getString(R.string.proxy_unreachable, endpoint.address());
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setAdapter(new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_list_item_1, entries),
                        (dialog, position) -> {
                            if (endpoints.get(position).reachable) {
                                applyProxy(endpoints.get(position));
                            } else {
                                Toast.makeText(requireContext(), R.string.proxy_apply_failed,
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyProxy(@NonNull final ProxyEndpoint endpoint) {
        if (!credentialStore.clearPassword()) {
            Toast.makeText(requireContext(), R.string.proxy_apply_failed,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(requireContext());
        preferences.edit()
                .putString(getString(R.string.proxy_mode_key),
                        getString(R.string.proxy_mode_http_value))
                .putString(getString(R.string.proxy_host_key), endpoint.host)
                .putString(getString(R.string.proxy_port_key), Integer.toString(endpoint.port))
                .putString(getString(R.string.proxy_username_key), "")
                .apply();
        AppProxySelector.refreshInstalled();
        hostPreference.setText(endpoint.host);
        portPreference.setText(Integer.toString(endpoint.port));
        updatePasswordSummary();
    }

    private void updatePasswordSummary() {
        passwordPreference.setSummary(credentialStore.hasPassword()
                ? R.string.proxy_password_configured
                : R.string.proxy_password_not_configured);
    }

    static boolean isValidHost(final String host) {
        if (host == null) {
            return false;
        }
        final String value = host.trim();
        return !value.isEmpty() && !value.contains("://") && !value.contains("/")
                && value.chars().noneMatch(Character::isWhitespace);
    }

    static boolean isValidPort(final String port) {
        try {
            final int value = Integer.parseInt(port);
            return value >= 1 && value <= 65_535;
        } catch (final NumberFormatException ignored) {
            return false;
        }
    }

    private static final class ProxyEndpoint {
        private final String host;
        private final int port;
        private final boolean reachable;
        private final long latencyMillis;

        private ProxyEndpoint(@NonNull final String host, final int port) {
            this(host, port, false, Long.MAX_VALUE);
        }

        private ProxyEndpoint(@NonNull final String host,
                              final int port,
                              final boolean reachable,
                              final long latencyMillis) {
            this.host = host;
            this.port = port;
            this.reachable = reachable;
            this.latencyMillis = latencyMillis;
        }

        @NonNull
        private ProxyEndpoint withLatency(final long value) {
            return new ProxyEndpoint(host, port, true, value);
        }

        @NonNull
        private ProxyEndpoint unreachable() {
            return new ProxyEndpoint(host, port, false, Long.MAX_VALUE);
        }

        @NonNull
        private String address() {
            return host + ":" + port;
        }
    }
}
