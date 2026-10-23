package com.github.yuyuanweb.mianshiyaplugin.reader;

import java.text.MessageFormat;
import java.util.ResourceBundle;

public final class ReaderMessages {
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("messages.MyBundle");
    private ReaderMessages() { }
    public static String text(String key, Object... arguments) {
        return MessageFormat.format(BUNDLE.getString("reader." + key), arguments);
    }
}
