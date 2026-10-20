package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.github.yuyuanweb.mianshiyaplugin.actions.QuestionBankAction;
import com.github.yuyuanweb.mianshiyaplugin.actions.LogoutAction;
import com.github.yuyuanweb.mianshiyaplugin.config.GlobalState;
import com.github.yuyuanweb.mianshiyaplugin.constant.CommonConstant;
import com.github.yuyuanweb.mianshiyaplugin.constant.IconConstant;
import com.github.yuyuanweb.mianshiyaplugin.reader.model.ReaderModels.*;
import com.github.yuyuanweb.mianshiyaplugin.view.LoginPanel;
import com.github.yuyuanweb.mianshiyaplugin.utils.ThemeUtil;
import com.github.yuyuanweb.mianshiyaplugin.view.QuestionListManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.Objects;

/** A list or independent question tab; navigation preserves the shared list instance. */
public final class SidebarPanel extends JPanel implements ReaderView, Disposable {
    private final Project project;
    private final ToolWindow window;
    private final SidebarController controller;
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final DefaultActionGroup actions = new DefaultActionGroup();
    private final QuestionReaderPanel reader;
    private QuestionBankAction bankBrowser;
    private QuestionListManager questionBrowser;
    private JComponent bankPanel;
    private JComponent questionPanel;
    private Long bankId;
    private URI sourceUri;
    private boolean detail, previous, next;
    private LoadStatus failureStatus;

    public SidebarPanel(Project project, ToolWindow window, SidebarController controller) {
        super(new BorderLayout());
        this.project = project; this.window = window; this.controller = controller;
        setMinimumSize(new Dimension(0, 0)); pages.setMinimumSize(new Dimension(0, 0));
        reader = new QuestionReaderPanel(project, controller::savePosition);
        Disposer.register(this, reader);
        pages.add(reader, "QUESTION_DETAIL");
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("MianshiyaReader", actions, true);
        toolbar.setTargetComponent(this);
        toolbar.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
        add(toolbar.getComponent(), BorderLayout.NORTH); add(pages, BorderLayout.CENTER);
        rebuildActions();
    }
    @Override public void showBanks() {
        if (bankPanel == null) {
            bankBrowser = new QuestionBankAction();
            bankPanel = bankBrowser.createPanel(project, controller::showQuestions);
            pages.add(bankPanel, "BANK_LIST");
        }
        detail = false; cards.show(pages, "BANK_LIST"); rebuildActions();
    }
    @Override public void showQuestions(Long selectedBank) {
        if (questionPanel == null || !Objects.equals(bankId, selectedBank)) {
            if (questionBrowser != null) questionBrowser.dispose();
            if (questionPanel != null) pages.remove(questionPanel);
            bankId = selectedBank;
            questionBrowser = new QuestionListManager();
            questionPanel = questionBrowser.createPanel(bankId, project, controller::showQuestion);
            pages.add(questionPanel, "QUESTION_LIST");
        }
        detail = false; cards.show(pages, "QUESTION_LIST"); rebuildActions();
    }
    @Override public void restoreQuestionList() {
        if (questionPanel == null) { showBanks(); return; }
        detail = false; cards.show(pages, "QUESTION_LIST"); rebuildActions();
    }
    @Override public void showLoading(QuestionSelection selection) {
        detail = true; failureStatus = null;
        sourceUri = URI.create(CommonConstant.WEB_HOST + "question/" + selection.getId());
        reader.showStatus(selection.getTitle() + "\n\n" + ReaderMessages.text("loading"));
        cards.show(pages, "QUESTION_DETAIL"); rebuildActions();
    }
    @Override public void showDocument(RenderedQuestion document, ReadingPosition position) {
        sourceUri = document.getSourceUri();
        reader.showDocument(document, position); cards.show(pages, "QUESTION_DETAIL");
    }
    @Override public void showFailure(QuestionLoadResult result) {
        failureStatus = result.getStatus();
        String key;
        switch (result.getStatus()) {
            case NEEDS_LOGIN: key = "status.login"; break;
            case FORBIDDEN: key = "status.forbidden"; break;
            case NOT_FOUND: key = "status.notfound"; break;
            default: key = "status.error";
        }
        reader.showStatus(result.getStatus() == LoadStatus.ERROR && !result.getMessage().isEmpty()
                ? result.getMessage() : ReaderMessages.text(key)); rebuildActions();
    }
    @Override public void updateNavigation(boolean hasPrevious, boolean hasNext) {
        previous = hasPrevious; next = hasNext; rebuildActions();
    }
    @Override public void clearSessionContent() {
        if (questionBrowser != null) questionBrowser.dispose();
        if (bankBrowser != null) bankBrowser.dispose();
        if (questionPanel != null) pages.remove(questionPanel);
        if (bankPanel != null) pages.remove(bankPanel);
        questionBrowser = null; bankBrowser = null; questionPanel = null; bankPanel = null; sourceUri = null;
        reader.clearSession(); showBanks();
    }
    private void rebuildActions() {
        actions.removeAll();
        if (detail) {
            actions.add(action("back", AllIcons.Actions.Back, controller::backToList));
            actions.add(new AnAction(ReaderMessages.text("previous"), null, AllIcons.Actions.Back) {
                @Override public void actionPerformed(@NotNull AnActionEvent e) { controller.previousQuestion(); }
                @Override public void update(@NotNull AnActionEvent e) { e.getPresentation().setEnabled(previous); }
            });
            actions.add(new AnAction(ReaderMessages.text("next"), null, AllIcons.Actions.Forward) {
                @Override public void actionPerformed(@NotNull AnActionEvent e) { controller.nextQuestion(); }
                @Override public void update(@NotNull AnActionEvent e) { e.getPresentation().setEnabled(next); }
            });
            if (failureStatus != null) actions.add(action("retry", AllIcons.Actions.Refresh, controller::retry));
            if (failureStatus == LoadStatus.NEEDS_LOGIN) actions.add(loginAction(true));
        } else {
            actions.add(action("banks", AllIcons.Scope.ChangedFilesAll, controller::showBanks));
            actions.add(action("questions", AllIcons.Scope.ChangedFiles, () -> controller.showQuestions(null)));
            actions.add(loginAction(false));
        }
        DefaultActionGroup more = new DefaultActionGroup(ReaderMessages.text("more"), true);
        more.getTemplatePresentation().setIcon(AllIcons.General.GearPlain);
        more.add(new AnAction(ReaderMessages.text("original")) {
            @Override public void actionPerformed(@NotNull AnActionEvent e) { if (sourceUri != null) BrowserUtil.browse(sourceUri.toString()); }
            @Override public void update(@NotNull AnActionEvent e) { e.getPresentation().setEnabled(sourceUri != null); }
        });
        if (detail) more.add(action("answer.toggle", null, reader::toggleAnswer));
        more.add(fontSizeAction());
        more.add(action("settings", null, () -> com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, ReaderSettingsConfigurable.class)));
        more.addSeparator(); more.add(action("banks", null, controller::showBanks));
        more.add(action("questions", null, () -> controller.showQuestions(null))); more.addSeparator();
        more.add(action("dock.left", null, () -> window.setAnchor(ToolWindowAnchor.LEFT, null)));
        more.add(action("dock.right", null, () -> window.setAnchor(ToolWindowAnchor.RIGHT, null)));
        more.add(action("dock.bottom", null, () -> window.setAnchor(ToolWindowAnchor.BOTTOM, null)));
        more.addSeparator(); more.add(loginAction(failureStatus == LoadStatus.NEEDS_LOGIN && detail));
        more.add(new LogoutAction(ReaderMessages.text("logout"), IconConstant.LOGOUT, new DefaultActionGroup()) {
            @Override public void update(@NotNull AnActionEvent e) {
                String cookie = GlobalState.getInstance().getSavedCookie();
                e.getPresentation().setEnabled(cookie != null && !cookie.isEmpty());
            }
        });
        actions.add(more); revalidate(); repaint();
    }
    private AnAction fontSizeAction() {
        return new AnAction(ReaderMessages.text("font.size", ThemeUtil.readerFontSize())) {
            @Override public void actionPerformed(@NotNull AnActionEvent e) {
                ReaderFontSizePopup.show(SidebarPanel.this);
            }
            @Override public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setText(ReaderMessages.text("font.size", ThemeUtil.readerFontSize()));
            }
        };
    }
    private AnAction loginAction(boolean forceLogin) {
        return new ReaderLoginAction(IconConstant.LOGIN, () -> {
            GlobalState state = GlobalState.getInstance();
            return state.getSavedUser() != null && state.getSavedUser().getId() != null
                    && state.getSavedCookie() != null && !state.getSavedCookie().isEmpty();
        }, () -> new LoginPanel(project).showAndGet(), forceLogin);
    }
    private AnAction action(String key, Icon icon, Runnable task) {
        return new AnAction(ReaderMessages.text(key), ReaderMessages.text(key), icon) {
            @Override public void actionPerformed(@NotNull AnActionEvent e) { task.run(); }
        };
    }
    @Override public void dispose() {
        if (questionBrowser != null) questionBrowser.dispose();
        if (bankBrowser != null) bankBrowser.dispose();
    }
}
