package com.druvu.lib.fx.bus;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;

/**
 * Typed publish/subscribe event bus with explicit threading semantics per subscription
 * (see {@link Delivery}). Events are plain objects - records by convention.
 *
 * Threading contract: {@link #publish(Object)} and {@link #subscribe} may be called from any
 * thread. Dispatch is polymorphic: a subscriber to a supertype receives all subtype events.
 * A handler that throws is logged and never breaks the publisher or other subscribers.
 */
@Slf4j
public final class FxBus {

	private final List<Sub<?>> subs = new CopyOnWriteArrayList<>();
	private final ThreadFactory asyncThreads = Thread.ofVirtual().name("druvu-fx-bus-", 0).factory();

	/**
	 * Subscribes a handler for events assignable to the given type.
	 * The returned {@link Subscription} must be closed when the subscriber's lifecycle ends.
	 */
	public <E> Subscription subscribe(Class<E> type, Delivery delivery, Consumer<? super E> handler) {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(delivery, "delivery");
		Objects.requireNonNull(handler, "handler");
		final Sub<E> sub = new Sub<>(this, type, delivery, handler);
		subs.add(sub);
		return sub;
	}

	/**
	 * Publishes the event to every matching subscriber, from any thread.
	 * Returns after CALLER-mode handlers have run; FX/ASYNC deliveries are in flight.
	 */
	public void publish(Object event) {
		Objects.requireNonNull(event, "event");
		for (Sub<?> sub : subs) {
			sub.offer(event);
		}
	}

	private void remove(Sub<?> sub) {
		subs.remove(sub);
	}

	private static final class Sub<E> implements Subscription {

		private final FxBus bus;
		private final Class<E> type;
		private final Delivery delivery;
		private final Consumer<? super E> handler;
		private final AtomicBoolean closed = new AtomicBoolean(false);

		private Sub(FxBus bus, Class<E> type, Delivery delivery, Consumer<? super E> handler) {
			this.bus = bus;
			this.type = type;
			this.delivery = delivery;
			this.handler = handler;
		}

		private void offer(Object event) {
			if (closed.get() || !type.isInstance(event)) {
				return;
			}
			final E typed = type.cast(event);
			switch (delivery) {
				case CALLER -> invoke(typed);
				case FX -> Platform.runLater(() -> invoke(typed));
				case ASYNC -> bus.asyncThreads.newThread(() -> invoke(typed)).start();
			}
		}

		private void invoke(E event) {
			if (closed.get()) {
				return;
			}
			try {
				handler.accept(event);
			} catch (RuntimeException ex) {
				log.error("Subscriber for {} failed on event {}", type.getName(), event, ex);
			}
		}

		@Override
		public void close() {
			if (closed.compareAndSet(false, true)) {
				bus.remove(this);
			}
		}
	}
}
