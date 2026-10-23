package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.intellij.openapi.Disposable;
import java.net.URI;

/** Owns at most one preview; document/session/project lifecycles all release it through close. */
final class ImagePreviewController implements Disposable {
    interface Preview { void show(); void close(); }
    interface Factory { Preview create(URI image, String alt); }
    private final Factory factory;
    private Preview current;
    private boolean disposed;
    ImagePreviewController(Factory factory) { this.factory = factory; }
    void open(String value, String alt) {
        if (disposed) return;
        URI uri = imageUri(value);
        if (uri == null) return;
        close();
        current = factory.create(uri, alt == null ? "" : alt);
        try { current.show(); } catch (RuntimeException error) { close(); throw error; }
    }
    static URI imageUri(String value) {
        if (value == null || value.length() > 8192) return null;
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null ? uri : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }
    void close() { Preview old = current; current = null; if (old != null) old.close(); }
    @Override public void dispose() { disposed = true; close(); }
}
