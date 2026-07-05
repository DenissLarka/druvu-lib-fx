package com.druvu.lib.fx.exec;

import java.time.Duration;

/**
 * Lifecycle events published on the bus by {@link FxExec} for every task it runs.
 * Subscribe to {@code TaskEvent} itself to observe all of them (the status bar does),
 * or to a single record type.
 *
 * Threading contract: published from the task's worker thread - choose the subscription
 * {@link com.druvu.lib.fx.bus.Delivery} accordingly.
 */
public sealed interface TaskEvent permits TaskEvent.Started, TaskEvent.Finished, TaskEvent.Failed {

	/**
	 * @return identity of the task, unique per {@link FxExec} instance
	 */
	long id();

	/**
	 * @return human-readable task title, as shown in status UIs
	 */
	String title();

	record Started(long id, String title) implements TaskEvent {
	}

	record Finished(long id, String title, Duration elapsed) implements TaskEvent {
	}

	record Failed(long id, String title, Throwable error, Duration elapsed) implements TaskEvent {
	}
}
