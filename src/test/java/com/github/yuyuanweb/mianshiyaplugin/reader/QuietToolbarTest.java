package com.github.yuyuanweb.mianshiyaplugin.reader;

import org.junit.Test;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class QuietToolbarTest {
    @Test public void controlsStayOnOneRowWithoutOverlapAtSidebarAndBottomWidths() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            QuietToolbar toolbar = new QuietToolbar(() -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {});
            for (int width : new int[]{180, 240, 280, 360, 480, 960, 1440}) {
                toolbar.setSize(width, 28); toolbar.doLayout();
                int right = 0;
                for (Component control : toolbar.getComponents()) {
                    assertTrue(control.getX() >= right);
                    assertEquals(0, control.getY()); assertEquals(28, control.getHeight());
                    assertTrue(control.getWidth() > 0);
                    right = control.getX() + control.getWidth();
                    assertTrue(right <= width);
                }
                assertEquals(width, right);
            }
            assertEquals("A−", ((JButton) toolbar.getComponent(4)).getText());
            assertEquals("A+", ((JButton) toolbar.getComponent(5)).getText());
            assertEquals("更多", toolbar.moreButton().getAccessibleContext().getAccessibleName());
        });
    }
    @Test public void disabledNavigationAndFontBoundaryControlsDoNotFire() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger previous = new AtomicInteger(), decrease = new AtomicInteger();
            QuietToolbar toolbar = new QuietToolbar(() -> {}, previous::incrementAndGet, () -> {}, () -> {},
                    decrease::incrementAndGet, () -> {}, () -> {});
            toolbar.update(false, false, false, false, true, "题库");
            ((JButton) toolbar.getComponent(1)).doClick();
            ((JButton) toolbar.getComponent(4)).doClick();
            assertEquals(0, previous.get()); assertEquals(0, decrease.get());
            toolbar.update(true, true, false, true, false, "返回列表");
            ((JButton) toolbar.getComponent(1)).doClick();
            ((JButton) toolbar.getComponent(4)).doClick();
            assertEquals(1, previous.get()); assertEquals(1, decrease.get());
            assertEquals("返回列表", ((JButton) toolbar.getComponent(0)).getAccessibleContext().getAccessibleName());
        });
    }
}
