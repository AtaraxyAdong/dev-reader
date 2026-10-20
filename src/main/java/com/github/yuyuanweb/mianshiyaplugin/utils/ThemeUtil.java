package com.github.yuyuanweb.mianshiyaplugin.utils;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.github.yuyuanweb.mianshiyaplugin.reader.ReaderAppearanceSettings;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import com.intellij.util.ui.UIUtil;

/**
 * 主题工具类
 *
 * @author pine
 */
public class ThemeUtil {

    /** Read actual UI colors rather than inheriting a website's theme. */
    public static Map<String, String> readerVariables() {
        Map<String, String> values = new LinkedHashMap<>();
        Color background = UIUtil.getPanelBackground();
        Color foreground = UIUtil.getLabelForeground();
        Font font = UIUtil.getLabelFont();
        values.put("--reader-bg", hex(background));
        values.put("--reader-fg", hex(foreground));
        values.put("--reader-muted", hex(blend(foreground, background, .65)));
        values.put("--reader-border", hex(blend(foreground, background, .18)));
        values.put("--reader-code-bg", hex(blend(foreground, background, .045)));
        values.put("--reader-link", hex(isDarkColor(background) ? new Color(120, 170, 235) : new Color(55, 105, 175)));
        values.put("--reader-font", "'" + font.getFamily().replace("'", "").replace("\\", "") + "', sans-serif");
        values.put("--reader-size", readerFontSize() + "px");
        return values;
    }

    public static int readerFontSize() {
        return ReaderAppearanceSettings.getInstance().fontSize(Math.max(12, UIUtil.getLabelFont().getSize()));
    }

    private static Color blend(Color first, Color second, double amount) {
        return new Color((int) (first.getRed() * amount + second.getRed() * (1 - amount)),
                (int) (first.getGreen() * amount + second.getGreen() * (1 - amount)),
                (int) (first.getBlue() * amount + second.getBlue() * (1 - amount)));
    }

    private static String hex(Color color) { return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }

    private static final String DARK = "dark";

    private static final String LIGHT = "light";

    /**
     * 判断当前颜色是不是暗色
     */
    public static boolean isDarkColor(Color color) {
        // 使用 RGB 值计算亮度
        double brightness = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());
        // 128 是亮度的阈值
        return brightness < 128;
    }

    /**
     * 获取该用的主题
     */
    public static String getTheme() {
        Color background = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
        boolean darkColor = ThemeUtil.isDarkColor(background);
        if (darkColor) {
            return DARK;
        } else {
            return LIGHT;
        }
    }

}
