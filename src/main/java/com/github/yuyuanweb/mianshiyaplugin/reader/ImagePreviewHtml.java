package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Trusted local document; URL/alt are JSON encoded, never interpolated into HTML attributes. */
final class ImagePreviewHtml {
    private ImagePreviewHtml() { }
    static String render(URI image, String alt, String closeScript, Map<String, String> theme) {
        if (image == null || ImagePreviewController.imageUri(image.toString()) == null)
            throw new IllegalArgumentException("Invalid image URL");
        Gson gson = new Gson();
        String script = resource("image-preview.js")
                + "\nwindow.readerPreviewClosed = function() {" + closeScript + "};"
                + "Object.entries(" + gson.toJson(theme) + ").forEach(function(pair){document.documentElement.style.setProperty(pair[0],pair[1]);});"
                + "window.readerShowImagePreview(" + gson.toJson(image.toString()) + "," + gson.toJson(alt) + ");";
        return resource("image-preview.html").replace("{{NONCE}}", UUID.randomUUID().toString())
                .replace("{{CSS}}", resource("reader.css")).replace("{{JS}}", script);
    }
    private static String resource(String name) {
        try (InputStream input = ImagePreviewHtml.class.getResourceAsStream("/reader/" + name)) {
            if (input == null) throw new IllegalStateException("Missing preview resource");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) { throw new IllegalStateException("Cannot load preview resource", error); }
    }
}
