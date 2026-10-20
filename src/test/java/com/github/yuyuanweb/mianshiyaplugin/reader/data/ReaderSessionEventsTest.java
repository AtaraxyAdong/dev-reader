package com.github.yuyuanweb.mianshiyaplugin.reader.data;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ReaderSessionEventsTest {

    @Test
    public void publishesOnlyGenerationAndUserIdentityAndCanUnsubscribe() throws Exception {
        AtomicLong receivedGeneration = new AtomicLong(-1);
        AtomicReference<Long> receivedUserId = new AtomicReference<>();
        ReaderSessionListener listener = (generation, userId) -> {
            receivedGeneration.set(generation);
            receivedUserId.set(userId);
        };

        AutoCloseable subscription = ReaderSessionEvents.addListener(listener);
        long firstGeneration = ReaderSessionEvents.publish(42L);

        assertEquals(firstGeneration, receivedGeneration.get());
        assertEquals(Long.valueOf(42L), receivedUserId.get());

        subscription.close();
        long nextGeneration = ReaderSessionEvents.publish(null);

        assertNotEquals(firstGeneration, nextGeneration);
        assertEquals(firstGeneration, receivedGeneration.get());
        assertEquals(Long.valueOf(42L), receivedUserId.get());
    }
}
