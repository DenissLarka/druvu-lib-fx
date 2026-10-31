package com.druvu.lib.fx.exec;

import java.time.Duration;

/**
 * Lifecycle events published on the bus by {@link FxExec} for every task it runs. Subscribe to {@code TaskEvent} itself
 * to observe all of them (the status bar does), or to a single record type.
 *
 * <p>Threading contract: published from the task's worker thread - choose the subscription
 * {@link com.druvu.lib.fx.bus.Delivery} accordingly.
 */
public sealed interface TaskEvent
        permits TaskEvent.Started, TaskEvent.Progressed, TaskEvent.Finished, TaskEvent.Failed {

    /** @return identity of the task, unique per {@link FxExec} instance */
    long id();

    /** @return human-readable task title, as shown in status UIs */
    String title();

    record Started(long id, String title) implements TaskEvent {}

    /**
     * How far a task is, published for work that reports through a {@link TaskProgress}.
     *
     * @param fraction from 0 to 1, or {@code -1} when the total is unknown
     */
    record Progressed(long id, String title, double fraction) implements TaskEvent {}

    record Finished(long id, String title, Duration elapsed) implements TaskEvent {}

    record Failed(long id, String title, Throwable error, Duration elapsed) implements TaskEvent {}
}
