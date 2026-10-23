package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/** Application-wide reading preferences, separate from login state. */
@Service
@State(name = "DevReaderAppearance", storages = @Storage("dev-reader-appearance.xml"))
public final class ReaderAppearanceSettings implements PersistentStateComponent<ReaderAppearanceSettings.Settings> {
    public static final String DEFAULT_TOOL_WINDOW_NAME = "阅读";
    public static final int MIN_FONT_SIZE = 10;
    public static final int MAX_FONT_SIZE = 28;
    public static final Topic<Runnable> CHANGED = Topic.create("dev-reader appearance changed", Runnable.class);

    public static final class Settings {
        public int fontSize = 0; // Zero follows the IDE font.
        public String toolWindowName = DEFAULT_TOOL_WINDOW_NAME;
    }

    private Settings settings = new Settings();

    public static ReaderAppearanceSettings getInstance() {
        return ApplicationManager.getApplication().getService(ReaderAppearanceSettings.class);
    }

    @Override public @NotNull Settings getState() { return settings; }

    @Override public void loadState(@NotNull Settings state) {
        settings = state;
        settings.fontSize = normalize(settings.fontSize);
        settings.toolWindowName = normalizeToolWindowName(settings.toolWindowName);
    }

    public int fontSize(int ideDefault) {
        return settings.fontSize == 0 ? ideDefault : settings.fontSize;
    }

    public boolean followsIde() { return settings.fontSize == 0; }

    public void setFontSize(int size) {
        int normalized = normalize(size);
        if (settings.fontSize == normalized) return;
        settings.fontSize = normalized;
        ApplicationManager.getApplication().getMessageBus().syncPublisher(CHANGED).run();
    }

    public String getToolWindowName() { return normalizeToolWindowName(settings.toolWindowName); }

    public void setToolWindowName(String name) {
        String normalized = normalizeToolWindowName(name);
        if (getToolWindowName().equals(normalized)) return;
        settings.toolWindowName = normalized;
        ApplicationManager.getApplication().getMessageBus().syncPublisher(CHANGED).run();
    }

    public static String normalizeToolWindowName(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.isEmpty() ? DEFAULT_TOOL_WINDOW_NAME : trimmed;
    }

    private static int normalize(int size) {
        return size == 0 ? 0 : Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
    }
}