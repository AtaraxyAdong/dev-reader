package com.github.yuyuanweb.mianshiyaplugin.config;

import com.github.yuyuanweb.mianshiyaplugin.constant.PageConstant;
import com.github.yuyuanweb.mianshiyaplugin.model.response.User;
import com.github.yuyuanweb.mianshiyaplugin.reader.data.ReaderSessionEvents;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 全局配置
 *
 * @author pine
 */
@State(name = "GlobalState", storages = {@Storage("mainshiya.xml")})
public class GlobalState implements PersistentStateComponent<GlobalState.State> {

    public static class State {
        public String cookie = "";
        public int pageSize = PageConstant.PAGE_SIZE;
        public User user = null;
    }

    private State state = new State();

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public void saveCookie(String cookie) {
        String previous = state.cookie;
        state.cookie = cookie;
        if (!java.util.Objects.equals(previous, cookie)) {
            publishSessionChange();
        }
    }

    public void saveUser(User user) {
        Long previousId = state.user == null ? null : state.user.getId();
        state.user = user;
        Long nextId = user == null ? null : user.getId();
        if (!java.util.Objects.equals(previousId, nextId)) {
            publishSessionChange();
        }
    }

    public String getSavedCookie() {
        return state.cookie;
    }

    public User getSavedUser() {
        return state.user;
    }

    public void removeSavedCookie() {
        boolean hadCookie = state.cookie != null && !state.cookie.isEmpty();
        state.cookie = "";
        if (hadCookie) {
            publishSessionChange();
        }
    }

    public void removeSavedUser() {
        Long previousId = state.user == null ? null : state.user.getId();
        state.user = null;
        if (previousId != null) {
            publishSessionChange();
        }
    }

    private void publishSessionChange() {
        ReaderSessionEvents.publish(state.user == null ? null : state.user.getId());
    }

    public static GlobalState getInstance() {
        return ApplicationManager.getApplication().getService(GlobalState.class);
    }
}
