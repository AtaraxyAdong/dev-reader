package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.intellij.openapi.actionSystem.Presentation;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ReaderLoginActionTest {
    @Test public void refreshesLoginPresentationAfterLoginAndLogout() {
        AtomicBoolean authenticated = new AtomicBoolean(false);
        AtomicInteger opened = new AtomicInteger();
        ReaderLoginAction action = new ReaderLoginAction(null, authenticated::get, opened::incrementAndGet, false);
        Presentation presentation = new Presentation();
        action.refresh(presentation);
        assertEquals(ReaderMessages.text("login"), presentation.getText());
        assertTrue(presentation.isEnabled());
        action.performLogin();
        assertEquals(1, opened.get());
        authenticated.set(true);
        action.refresh(presentation);
        assertEquals(ReaderMessages.text("logged.in"), presentation.getText());
        assertFalse(presentation.isEnabled());
        action.performLogin();
        assertEquals(1, opened.get());
        authenticated.set(false);
        action.refresh(presentation);
        assertEquals(ReaderMessages.text("login"), presentation.getText());
        assertTrue(presentation.isEnabled());
    }

    @Test public void expiredSessionCanLoginAgainEvenWithSavedUser() {
        AtomicInteger opened = new AtomicInteger();
        ReaderLoginAction action = new ReaderLoginAction(null, () -> true, opened::incrementAndGet, true);
        Presentation presentation = new Presentation();
        action.refresh(presentation);
        assertEquals(ReaderMessages.text("login"), presentation.getText());
        assertTrue(presentation.isEnabled());
        action.performLogin();
        assertEquals(1, opened.get());
    }

}
