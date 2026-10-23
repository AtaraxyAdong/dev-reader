package com.github.yuyuanweb.mianshiyaplugin.reader.data;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Process-wide, credential-free session change notifications for project readers. */
public final class ReaderSessionEvents {
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final CopyOnWriteArrayList<ReaderSessionListener> LISTENERS = new CopyOnWriteArrayList<>();

    private ReaderSessionEvents() { }

    public static AutoCloseable addListener(ReaderSessionListener listener) {
        LISTENERS.addIfAbsent(listener);
        return () -> LISTENERS.remove(listener);
    }

    public static long publish(Long userId) {
        long generation = GENERATION.incrementAndGet();
        for (ReaderSessionListener listener : LISTENERS) {
            try {
                listener.onSessionChanged(generation, userId);
            } catch (RuntimeException ignored) {
                // A faulty project listener must not prevent other projects from clearing their state.
            }
        }
        return generation;
    }

    public static long currentGeneration() {
        return GENERATION.get();
    }
}
