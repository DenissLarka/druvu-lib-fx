package com.druvu.lib.fx.bus;

/**
 * Handle to an active {@link FxBus} subscription. Close it when the owning view/component goes away - subscriptions are
 * deliberately explicit, never weakly referenced.
 */
public interface Subscription extends AutoCloseable {

    /**
     * Unsubscribes. Idempotent, callable from any thread. No handler invocation is started after close() returns; an
     * invocation already running on another thread may complete.
     */
    @Override
    void close();
}
