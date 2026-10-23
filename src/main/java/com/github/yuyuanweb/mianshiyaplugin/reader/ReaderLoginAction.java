package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;
import javax.swing.Icon;
import java.util.function.BooleanSupplier;

/** Reads current session state on each toolbar update; expired sessions can explicitly sign in again. */
final class ReaderLoginAction extends AnAction {
    private final BooleanSupplier authenticated;
    private final Runnable login;
    private final boolean forceLogin;

    ReaderLoginAction(Icon icon, BooleanSupplier authenticated, Runnable login, boolean forceLogin) {
        super(ReaderMessages.text("login"), ReaderMessages.text("login"), icon);
        this.authenticated = authenticated;
        this.login = login;
        this.forceLogin = forceLogin;
    }

    @Override public void update(@NotNull AnActionEvent event) {
        refresh(event.getPresentation());
    }

    void refresh(Presentation presentation) {
        boolean loggedIn = !forceLogin && authenticated.getAsBoolean();
        presentation.setText(ReaderMessages.text(loggedIn ? "logged.in" : "login"));
        presentation.setEnabled(!loggedIn);
    }

    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        performLogin();
    }

    void performLogin() {
        if (forceLogin || !authenticated.getAsBoolean()) login.run();
    }
}
