package com.druvu.lib.fx.status;

import com.druvu.lib.fx.bus.Delivery;
import com.druvu.lib.fx.bus.FxBus;
import com.druvu.lib.fx.bus.Subscription;
import com.druvu.lib.fx.exec.TaskEvent;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/**
 * Observable status-bar state derived from {@link TaskEvent}s on the bus: how many tasks are
 * running, whether any are, and a one-line description of the latest activity. An app binds a
 * status bar's labels/spinner to these properties - no polling, no threading in the view.
 *
 * <p>Subscribes with {@link Delivery#FX}, so every update lands on the JavaFX thread and the
 * properties are safe to bind directly. Construct after the toolkit is started; {@link #close()}
 * to detach from the bus (e.g. in {@code Application.stop()}).
 */
public final class StatusBarModel implements AutoCloseable {

	private final ReadOnlyIntegerWrapper runningTasks = new ReadOnlyIntegerWrapper(this, "runningTasks", 0);
	private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(this, "busy", false);
	private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");
	private final Subscription subscription;

	/**
	 * Starts observing {@link TaskEvent}s published on the given bus.
	 *
	 * @param bus the event bus {@link com.druvu.lib.fx.exec.FxExec} publishes task events on
	 */
	public StatusBarModel(FxBus bus) {
		this.subscription = bus.subscribe(TaskEvent.class, Delivery.FX, this::onEvent);
	}

	private void onEvent(TaskEvent event) {
		switch (event) {
			case TaskEvent.Started s -> {
				runningTasks.set(runningTasks.get() + 1);
				message.set("running: " + s.title());
			}
			case TaskEvent.Finished f -> {
				runningTasks.set(runningTasks.get() - 1);
				message.set("done: " + f.title());
			}
			case TaskEvent.Failed x -> {
				runningTasks.set(runningTasks.get() - 1);
				message.set("failed: " + x.title());
			}
		}
		busy.set(runningTasks.get() > 0);
	}

	/** @return number of tasks currently running (never negative under normal use). */
	public ReadOnlyIntegerProperty runningTasksProperty() {
		return runningTasks.getReadOnlyProperty();
	}

	/** @return {@code true} while at least one task is running. */
	public ReadOnlyBooleanProperty busyProperty() {
		return busy.getReadOnlyProperty();
	}

	/** @return one-line description of the most recent task transition. */
	public ReadOnlyStringProperty messageProperty() {
		return message.getReadOnlyProperty();
	}

	/** Detaches from the bus. Idempotent. */
	@Override
	public void close() {
		subscription.close();
	}
}
