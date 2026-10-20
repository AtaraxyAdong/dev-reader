package com.github.yuyuanweb.mianshiyaplugin.reader;

import java.awt.Insets;
import java.awt.Rectangle;
import java.util.List;

/** AWT logical coordinates preserve negative monitor origins and per-screen work areas. */
final class ImagePreviewPlacement {
    private ImagePreviewPlacement() { }
    static Rectangle usable(Rectangle screen, Insets insets) {
        return new Rectangle(screen.x + insets.left, screen.y + insets.top,
                Math.max(1, screen.width - insets.left - insets.right),
                Math.max(1, screen.height - insets.top - insets.bottom));
    }
    static Rectangle centered(Rectangle owner, List<Rectangle> screens) {
        if (screens.isEmpty()) throw new IllegalArgumentException("A screen is required");
        Rectangle chosen = screens.get(0);
        double bestArea = -1, bestDistance = Double.POSITIVE_INFINITY;
        for (Rectangle screen : screens) {
            Rectangle intersection = owner == null ? new Rectangle() : screen.intersection(owner);
            double area = Math.max(0, intersection.width) * (double) Math.max(0, intersection.height);
            double distance = owner == null ? 0 : Math.pow(screen.getCenterX() - owner.getCenterX(), 2)
                    + Math.pow(screen.getCenterY() - owner.getCenterY(), 2);
            if (area > bestArea || (area == bestArea && distance < bestDistance)) {
                chosen = screen; bestArea = area; bestDistance = distance;
            }
        }
        int width = Math.min(chosen.width, Math.max(320, Math.min(1200, (int) Math.round(chosen.width * .85))));
        int height = Math.min(chosen.height, Math.max(240, Math.min(900, (int) Math.round(chosen.height * .85))));
        return new Rectangle(chosen.x + (chosen.width - width) / 2, chosen.y + (chosen.height - height) / 2, width, height);
    }
}
