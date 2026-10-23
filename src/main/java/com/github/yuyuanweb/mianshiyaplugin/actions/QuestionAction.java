package com.github.yuyuanweb.mianshiyaplugin.actions;

import com.github.yuyuanweb.mianshiyaplugin.reader.SidebarController;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author pine
 */
public class QuestionAction extends AnAction {

    public QuestionAction() {
    }

    public QuestionAction(String text, Icon icon) {
        super(text, text, icon);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        com.intellij.openapi.project.Project project = anActionEvent.getProject();
        if (project == null || project.isDisposed()) return;
        SidebarController.getInstance(project).showQuestions(null);
    }

}
