package org.opentripplanner.middleware.utils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple {@link AtomicBoolean} wrapper so that only one thread at a time can assert a claim (e.g. on web requests).
 * The claim method can be used in try/finally blocks to ensure that the claim is reset in exception workflows.
 */
public class AtomicAvailability implements AutoCloseable {
    private final AtomicBoolean claimed = new AtomicBoolean();

    public boolean claim() {
        return claimed.compareAndSet(false, true);
    }

    public void release() {
        claimed.set(false);
    }

    @Override
    public void close() {
        release();
    }
}
