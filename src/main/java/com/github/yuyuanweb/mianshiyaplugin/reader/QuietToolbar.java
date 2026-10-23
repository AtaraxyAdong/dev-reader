package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.intellij.icons.AllIcons;
import com.intellij.util.ui.JBUI;
import javax.swing.*;
import java.awt.*;

/** Same compact control order as the shared reading plugin; never wraps. */
final class QuietToolbar extends JPanel {
    private final JButton directory, previous, next, refresh, decrease, increase, more;

    QuietToolbar(Runnable onDirectory, Runnable onPrevious, Runnable onNext, Runnable onRefresh,
                 Runnable onDecrease, Runnable onIncrease, Runnable onMore) {
        setLayout(null);
        directory = icon(AllIcons.Actions.MenuOpen, "返回列表", onDirectory);
        previous = icon(AllIcons.Actions.Back, "上一题", onPrevious);
        next = icon(AllIcons.Actions.Forward, "下一题", onNext);
        refresh = icon(AllIcons.Actions.Refresh, "刷新", onRefresh);
        decrease = button("A−", "缩小字号", onDecrease);
        increase = button("A+", "放大字号", onIncrease);
        more = icon(AllIcons.Actions.MoreHorizontal, "更多", onMore);
        setPreferredSize(JBUI.size(240, 28));
        setMinimumSize(JBUI.size(180, 28));
    }
    private JButton icon(Icon icon, String name, Runnable callback) {
        JButton button = button("", name, callback);
        button.setIcon(icon);
        return button;
    }
    private JButton button(String text, String name, Runnable callback) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        button.addActionListener(e -> callback.run());
        add(button);
        return button;
    }
    @Override public void doLayout() {
        int unit = Math.min(JBUI.scale(28), Math.max(0, getWidth() / 7));
        int h = getHeight();
        directory.setBounds(0, 0, unit, h);
        previous.setBounds(unit, 0, unit, h);
        next.setBounds(unit * 2, 0, unit, h);
        refresh.setBounds(unit * 3, 0, unit, h);
        decrease.setBounds(getWidth() - unit * 3, 0, unit, h);
        increase.setBounds(getWidth() - unit * 2, 0, unit, h);
        more.setBounds(getWidth() - unit, 0, unit, h);
    }
    void update(boolean canReturn, boolean canPrevious, boolean canNext,
                boolean canDecrease, boolean canIncrease, String returnLabel) {
        directory.setEnabled(canReturn);
        directory.setToolTipText(returnLabel);
        directory.getAccessibleContext().setAccessibleName(returnLabel);
        previous.setEnabled(canPrevious); next.setEnabled(canNext);
        decrease.setEnabled(canDecrease); increase.setEnabled(canIncrease);
    }
    JButton moreButton() { return more; }
}
