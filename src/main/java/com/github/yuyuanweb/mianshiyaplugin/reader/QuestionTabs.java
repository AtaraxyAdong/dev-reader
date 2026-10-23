package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.github.yuyuanweb.mianshiyaplugin.reader.data.ReaderSessionEvents;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.QuestionSelection;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/** Owns native question tabs; the fixed reader content retains its list and search state. */
public final class QuestionTabs implements Disposable {
    private final ContentManager manager;
    private final Content listContent;
    private final SidebarController listController;
    private final Function<QuestionSelection, Tab> createTab;
    private final Consumer<Runnable> ui;
    private final BooleanSupplier projectDisposed;
    private final List<Tab> tabs = new ArrayList<>();
    private final ContentManagerListener contentListener;
    private final AutoCloseable sessionSubscription;
    private long sessionGeneration = ReaderSessionEvents.currentGeneration();
    private volatile boolean disposed;

    public QuestionTabs(Project project, ToolWindow window, Content listContent,
                        SidebarController listController, ContentFactory factory) {
        this(window.getContentManager(), listContent, listController, selection -> {
            SidebarController controller = new SidebarController(project, listController);
            SidebarPanel panel = new SidebarPanel(project, window, controller);
            controller.attachView(panel);
            Disposer.register(controller, panel);
            Content content = factory.createContent(panel, tabTitle(selection.getTitle()), false);
            content.setCloseable(true);
            content.setDisposer(controller);
            return new Tab(content, controller);
        }, task -> {
            if (ApplicationManager.getApplication().isDispatchThread()) task.run();
            else ApplicationManager.getApplication().invokeLater(task);
        }, project::isDisposed);
    }

    QuestionTabs(ContentManager manager, Content listContent, SidebarController listController,
                 Function<QuestionSelection, Tab> createTab, Consumer<Runnable> ui,
                 BooleanSupplier projectDisposed) {
        this.manager = manager;
        this.listContent = listContent;
        this.listController = listController;
        this.createTab = createTab;
        this.ui = ui;
        this.projectDisposed = projectDisposed;
        contentListener = new ContentManagerListener() {
            @Override public void contentRemoved(ContentManagerEvent event) {
                tabs.removeIf(tab -> tab.content == event.getContent());
            }
        };
        manager.addContentManagerListener(contentListener);
        listController.configureTabs(this::open, () -> select(listContent), null, title -> { });
        sessionSubscription = ReaderSessionEvents.addListener((generation, userId) ->
                ui.accept(() -> {
                    if (!unavailable()) clearForSession(generation);
                }));
    }

    public void open(QuestionSelection selection) {
        ui.accept(() -> {
            if (unavailable()) return;
            clearForSession(ReaderSessionEvents.currentGeneration());
            Tab existing = tabs.stream().filter(tab -> tab.questionId == selection.getId()).findFirst().orElse(null);
            if (existing != null) {
                select(existing.content);
                return;
            }
            Tab tab = createTab.apply(selection);
            tab.questionId = selection.getId();
            tabs.add(tab);
            tab.controller.configureTabs(this::open, () -> select(tab.content), listController,
                    title -> updateTitle(tab, title));
            tab.controller.setInTabNavigator(next -> navigate(tab, next));
            updateTitle(tab, selection.getTitle());
            manager.addContent(tab.content);
            select(tab.content);
            tab.controller.openInCurrentTab(selection);
        });
    }

    /** Previous/next stays in the originating native content, even when another tab has the same question. */
    private void navigate(Tab tab, QuestionSelection selection) {
        ui.accept(() -> {
            if (unavailable()) return;
            clearForSession(ReaderSessionEvents.currentGeneration());
            if (!tabs.contains(tab)) return;
            tab.questionId = selection.getId();
            updateTitle(tab, selection.getTitle());
            select(tab.content);
            tab.controller.openInCurrentTab(selection);
        });
    }
    private void updateTitle(Tab tab, String title) {
        if (unavailable() || !tabs.contains(tab)) return;
        tab.content.setDisplayName(tabTitle(title));
        tab.content.setDescription(title);
    }

    private void select(Content content) {
        if (!unavailable() && manager.getIndexOfContent(content) >= 0) manager.setSelectedContent(content);
    }

    private void clearForSession(long generation) {
        if (generation <= sessionGeneration) return;
        sessionGeneration = generation;
        closeQuestions();
        select(listContent);
    }

    private boolean unavailable() {
        return disposed || projectDisposed.getAsBoolean() || manager.isDisposed();
    }

    private void closeQuestions() {
        ArrayList<Tab> closing = new ArrayList<>(tabs);
        tabs.clear();
        for (Tab tab : closing) {
            if (manager.isDisposed()) continue; // The content manager owns disposal during IDE shutdown.
            if (!manager.removeContent(tab.content, true) && !Disposer.isDisposed(tab.controller)) {
                Disposer.dispose(tab.controller);
            }
        }
    }

    static String tabTitle(String title) {
        String text = title == null ? "" : title.trim();
        int length = text.codePointCount(0, text.length());
        return length <= 24 ? text : text.substring(0, text.offsetByCodePoints(0, 24)) + "…";
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        try { sessionSubscription.close(); } catch (Exception ignored) { }
        manager.removeContentManagerListener(contentListener);
        closeQuestions();
    }

    static final class Tab {
        final Content content;
        final SidebarController controller;
        long questionId;
        Tab(Content content, SidebarController controller) {
            this.content = content;
            this.controller = controller;
        }
    }
}
