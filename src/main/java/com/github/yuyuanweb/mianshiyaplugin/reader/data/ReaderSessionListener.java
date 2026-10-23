package com.github.yuyuanweb.mianshiyaplugin.reader.data;

/** Receives application-wide login, logout, and account-switch notifications. */
public interface ReaderSessionListener {
    void onSessionChanged(long generation, Long userId);
}
