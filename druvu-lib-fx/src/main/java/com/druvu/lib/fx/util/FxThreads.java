package com.druvu.lib.fx.util;

import java.util.Objects;
import java.util.concurrent.Executor;
import javafx.application.Platform;

/**
 * Thread-affinity primitives for the JavaFX application thread. Every druvu-lib-fx API states its threading contract in
 * these terms.
 */
public final class FxThreads {

    private FxThreads() {}

    /** @return true when the caller is on the JavaFX application thread */
    public static boolean isFx() {
        return Platform.isFxApplicationThread();
    }

    /**
     * Runs the action on the JavaFX application thread: immediately when the caller is already there, otherwise via
     * {@link Platform#runLater(Runnable)}.
     */
    public static void onFx(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (isFx()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    /**
     * Guards entry points that mutate live scene-graph state.
     *
     * @throws IllegalStateException when the caller is not on the JavaFX application thread
     */
    public static void requireFx() {
        if (!isFx()) {
            throw new IllegalStateException("Must be called on the JavaFX application thread, was: "
                    + Thread.currentThread().getName());
        }
    }

    /**
     * Guards entry points that block or perform IO.
     *
     * @throws IllegalStateException when the caller is on the JavaFX application thread
     */
    public static void requireOffFx() {
        if (isFx()) {
            throw new IllegalStateException("Must not be called on the JavaFX application thread");
        }
    }

    /**
     * An {@link Executor} dispatching every command via {@link Platform#runLater(Runnable)}. Composes with
     * {@code CompletableFuture}'s {@code *Async(..., executor)} methods to hop a continuation onto the FX thread:
     * {@code exec.supply("load", this::fetch).thenAcceptAsync(view::show, FxThreads.fxExecutor())}
     */
    public static Executor fxExecutor() {
        return Platform::runLater;
    }
}
