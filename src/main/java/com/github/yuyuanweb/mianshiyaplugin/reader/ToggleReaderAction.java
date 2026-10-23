package com.github.yuyuanweb.mianshiyaplugin.reader;
import com.github.yuyuanweb.mianshiyaplugin.constant.KeyConstant;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public final class ToggleReaderAction extends AnAction implements DumbAware {
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(KeyConstant.PLUGIN_NAME);
        if (window != null) { if (window.isVisible()) window.hide(null); else window.show(null); }
    }
    @Override public void update(@NotNull AnActionEvent event) { event.getPresentation().setEnabledAndVisible(event.getProject() != null); }
}
