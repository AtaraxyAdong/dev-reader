package com.github.yuyuanweb.mianshiyaplugin.reader;

import javax.swing.*;
import java.awt.event.*;

/** Single click selects; only an intentional left double click or Enter opens a row. */
public final class TableActivation {
    private TableActivation() { }
    public static void install(JTable table, Runnable openSelected) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2 && row >= 0) {
                    table.setRowSelectionInterval(row, row);
                    openSelected.run();
                }
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "reader.openSelected");
        table.getActionMap().put("reader.openSelected", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                if (table.getSelectedRow() >= 0) openSelected.run();
            }
        });
    }
}
