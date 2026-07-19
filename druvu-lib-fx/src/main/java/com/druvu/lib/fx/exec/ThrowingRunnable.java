package com.druvu.lib.fx.exec;

/** A {@link Runnable} that may throw - background work is usually IO with checked exceptions. */
@FunctionalInterface
public interface ThrowingRunnable {

    void run() throws Exception;
}
