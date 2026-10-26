package org.schabi.newpipe.util;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class MaterialTubeFont {
    private static final String TAG = MaterialTubeFont.class.getSimpleName();
    private static final String FONT_URI_KEY = "materialtube_font_uri";
    private static final int MAX_FONT_BYTES = 16 * 1024 * 1024;
    @Nullable
    private static String loadedUri;
    @Nullable
    private static Typeface loadedTypeface;

    private MaterialTubeFont() {
    }

    public static void applyToViewTree(@NonNull final Context context,
                                       @NonNull final View root) {
        final String uri = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(FONT_URI_KEY, null);
        if (uri == null) {
            return;
        }
        final Typeface typeface = loadTypeface(context, uri);
        if (typeface == null) {
            return;
        }
        applyTypeface(root, typeface);
    }

    @Nullable
    private static synchronized Typeface loadTypeface(@NonNull final Context context,
                                                       @NonNull final String uriValue) {
        if (uriValue.equals(loadedUri)) {
            return loadedTypeface;
        }
        final File fontFile = new File(context.getCacheDir(),
                "materialtube-font-" + Integer.toHexString(uriValue.hashCode()));
        try {
            if (!fontFile.isFile()) {
                copyFont(context, Uri.parse(uriValue), fontFile);
            }
            loadedTypeface = Typeface.createFromFile(fontFile);
            loadedUri = uriValue;
            return loadedTypeface;
        } catch (final IOException | RuntimeException e) {
            if (fontFile.exists() && !fontFile.delete()) {
                Log.w(TAG, "Could not remove the incomplete cached font file");
            }
            Log.e(TAG, "Could not load the selected font", e);
            return null;
        }
    }

    private static void copyFont(@NonNull final Context context,
                                 @NonNull final Uri uri,
                                 @NonNull final File destination) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Selected font could not be opened");
            }
            try (FileOutputStream output = new FileOutputStream(destination)) {
                final byte[] buffer = new byte[8192];
                int totalBytes = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    totalBytes += count;
                    if (totalBytes > MAX_FONT_BYTES) {
                        throw new IOException("Selected font exceeds the size limit");
                    }
                    output.write(buffer, 0, count);
                }
            }
        }
    }

    private static void applyTypeface(@NonNull final View view,
                                      @NonNull final Typeface typeface) {
        if (view instanceof TextView) {
            final TextView textView = (TextView) view;
            textView.setTypeface(typeface, textView.getTypeface() == null
                    ? Typeface.NORMAL : textView.getTypeface().getStyle());
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTypeface(group.getChildAt(i), typeface);
            }
        }
    }
}
