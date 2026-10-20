package com.github.yuyuanweb.mianshiyaplugin.toolWindow;

import com.github.yuyuanweb.mianshiyaplugin.reader.ReaderMessages;
import com.github.yuyuanweb.mianshiyaplugin.reader.ReaderAppearanceSettings;
import com.github.yuyuanweb.mianshiyaplugin.reader.QuestionTabs;
import com.github.yuyuanweb.mianshiyaplugin.reader.SidebarController;
import com.github.yuyuanweb.mianshiyaplugin.reader.SidebarPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/** Creates one reader without blocking network calls on EDT. */
public final class MyToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override public void init(@NotNull ToolWindow toolWindow) {
        updateWindowName(toolWindow);
        ApplicationManager.getApplication().getMessageBus().connect(toolWindow.getDisposable())
                .subscribe(ReaderAppearanceSettings.CHANGED, () -> updateWindowName(toolWindow));
    }

    private static void updateWindowName(ToolWindow toolWindow) {
        Runnable update = () -> {
            if (toolWindow.isDisposed()) return;
            String name = ReaderAppearanceSettings.getInstance().getToolWindowName();
            toolWindow.setTitle(name);
            toolWindow.setStripeTitle(name);
        };
        if (ApplicationManager.getApplication().isDispatchThread()) update.run();
        else ApplicationManager.getApplication().invokeLater(update);
    }

    @Override public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        SidebarController controller = SidebarController.getInstance(project);
        SidebarPanel panel = new SidebarPanel(project, toolWindow, controller);
        controller.attachView(panel);
        Disposer.register(controller, panel);
        ContentFactory factory = ApplicationManager.getApplication().getService(ContentFactory.class);
        Content content = factory.createContent(panel, ReaderMessages.text("title"), false);
        content.setCloseable(false);
        content.setDisposer(controller);
        toolWindow.getContentManager().addContent(content);
        QuestionTabs tabs = new QuestionTabs(project, toolWindow, content, controller, factory);
        Disposer.register(controller, tabs);
        controller.showBanks();
    }
}
