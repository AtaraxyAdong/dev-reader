package com.github.yuyuanweb.mianshiyaplugin.reader;
import org.junit.Test;
import java.awt.*;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ImagePreviewPlacementTest {
    @Test public void centersOnTheIdeMonitorInsteadOfPrimary() {
        Rectangle actual = ImagePreviewPlacement.centered(new Rectangle(2100, 100, 1000, 700),
                Arrays.asList(new Rectangle(0, 0, 1920, 1040), new Rectangle(1920, 0, 2560, 1400)));
        assertEquals(new Rectangle(2600, 250, 1200, 900), actual);
    }
    @Test public void supportsNegativeMonitorOriginsAndTaskbarInsets() {
        Rectangle usable = ImagePreviewPlacement.usable(new Rectangle(-1920, -100, 1920, 1080), new Insets(0, 40, 40, 0));
        assertEquals(new Rectangle(-1880, -100, 1880, 1040), usable);
        Rectangle actual = ImagePreviewPlacement.centered(new Rectangle(-1800, 0, 1000, 700),
                Arrays.asList(new Rectangle(0, 0, 1920, 1080), usable));
        assertEquals(new Rectangle(-1540, -22, 1200, 884), actual);
        assertTrue(usable.contains(actual));
    }
    @Test public void selectsLargestIntersectionForWindowAcrossScreens() {
        Rectangle actual = ImagePreviewPlacement.centered(new Rectangle(1500, 50, 1400, 900),
                Arrays.asList(new Rectangle(0, 0, 1920, 1040), new Rectangle(1920, 0, 1280, 984)));
        assertEquals(new Rectangle(2016, 74, 1088, 836), actual);
    }
    @Test public void smallScreenAndOffScreenOwnerRemainWithinTheNearestScreen() {
        Rectangle screen = new Rectangle(1000, 200, 300, 180);
        assertEquals(screen, ImagePreviewPlacement.centered(new Rectangle(1350, 220, 10, 10), Arrays.asList(screen)));
    }
}
