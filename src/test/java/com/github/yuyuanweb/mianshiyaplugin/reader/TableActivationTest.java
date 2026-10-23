package com.github.yuyuanweb.mianshiyaplugin.reader;

import org.junit.Test;
import javax.swing.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class TableActivationTest {
    @Test public void singleClickAndRightDoubleClickDoNotOpenButLeftDoubleClickOpensClickedRow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(new String[][]{{"first"}, {"second"}}, new String[]{"title"});
            table.setSize(240, 120);
            List<Integer> opened = new ArrayList<>();
            TableActivation.install(table, () -> opened.add(table.getSelectedRow()));
            table.setRowSelectionInterval(0, 0);
            click(table, 1, MouseEvent.BUTTON1, 4);
            click(table, 2, MouseEvent.BUTTON3, 4);
            click(table, 2, MouseEvent.BUTTON1, 110);
            assertTrue(opened.isEmpty());
            click(table, 2, MouseEvent.BUTTON1, table.getRowHeight() + 4);
            assertEquals(java.util.Collections.singletonList(1), opened);
        });
    }
    @Test public void enterRequiresSelectionAndPreservesTheSelectedRow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(new String[][]{{"bank"}, {"question"}}, new String[]{"title"});
            List<Integer> opened = new ArrayList<>();
            TableActivation.install(table, () -> opened.add(table.getSelectedRow()));
            Object key = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
            Action action = table.getActionMap().get(key);
            action.actionPerformed(new ActionEvent(table, 0, "Enter"));
            assertTrue(opened.isEmpty());
            table.setRowSelectionInterval(1, 1);
            action.actionPerformed(new ActionEvent(table, 0, "Enter"));
            assertEquals(java.util.Collections.singletonList(1), opened);
            assertEquals(1, table.getSelectedRow());
        });
    }
    private static void click(JTable table, int clicks, int button, int y) {
        MouseEvent event = new MouseEvent(table, MouseEvent.MOUSE_CLICKED, 1, 0, 4, y, clicks, false, button);
        for (MouseListener listener : table.getMouseListeners()) listener.mouseClicked(event);
    }
}
