package com.druvu.lib.fx.bus;

/**
 * Where a subscriber's handler runs, relative to the thread that called
 * {@link FxBus#publish(Object)}.
 */
public enum Delivery {

	/**
	 * Handler runs on the JavaFX application thread, always dispatched via
	 * {@code Platform.runLater} - even when the publisher is already on the FX thread.
	 * Predictable ordering, no reentrancy; costs at most one frame of latency.
	 */
	FX,

	/**
	 * Handler runs on a fresh virtual thread. For handlers that block or perform IO.
	 */
	ASYNC,

	/**
	 * Handler runs synchronously on the publishing thread, inside the {@code publish} call.
	 * For cheap, non-blocking handlers that need no thread hop.
	 */
	CALLER
}
