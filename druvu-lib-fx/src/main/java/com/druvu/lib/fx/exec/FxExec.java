package com.druvu.lib.fx.exec;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.druvu.lib.fx.bus.FxBus;

import lombok.extern.slf4j.Slf4j;

/**
 * Background execution on virtual threads, with task lifecycle published as
 * {@link TaskEvent}s on the bus - so status UIs observe work without the executor
 * knowing about them.
 *
 * Threading contract: {@code supply}/{@code run} may be called from any thread (typically
 * the FX thread) and never block; the work runs on a fresh virtual thread. To continue on
 * the FX thread, compose the returned future with
 * {@code .thenAcceptAsync(v -> ..., FxThreads.fxExecutor())}.
 */
@Slf4j
public final class FxExec implements AutoCloseable {

	private final ExecutorService executor =
			Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("druvu-fx-exec-", 0).factory());
	private final AtomicLong ids = new AtomicLong();
	private final Consumer<Object> events;

	/**
	 * Executor without bus wiring: tasks run, no events are published.
	 */
	public FxExec() {
		this.events = event -> {
		};
	}

	/**
	 * Executor publishing {@link TaskEvent}s on the given bus.
	 */
	public FxExec(FxBus bus) {
		this.events = Objects.requireNonNull(bus, "bus")::publish;
	}

	/**
	 * Runs value-returning work on a virtual thread.
	 *
	 * @return future completed with the result, or exceptionally with the work's exception
	 * @throws java.util.concurrent.RejectedExecutionException when called after {@link #close()}
	 */
	public <T> CompletableFuture<T> supply(String title, Callable<T> work) {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(work, "work");
		final long id = ids.incrementAndGet();
		final CompletableFuture<T> future = new CompletableFuture<>();
		executor.execute(() -> {
			final long startNanos = System.nanoTime();
			events.accept(new TaskEvent.Started(id, title));
			try {
				final T value = work.call();
				events.accept(new TaskEvent.Finished(id, title, elapsed(startNanos)));
				future.complete(value);
			} catch (Exception ex) {
				log.error("Task '{}' failed", title, ex);
				events.accept(new TaskEvent.Failed(id, title, ex, elapsed(startNanos)));
				future.completeExceptionally(ex);
			}
		});
		return future;
	}

	/**
	 * Runs void work on a virtual thread. Same contract as {@link #supply(String, Callable)}.
	 */
	public CompletableFuture<Void> run(String title, ThrowingRunnable work) {
		Objects.requireNonNull(work, "work");
		return supply(title, () -> {
			work.run();
			return null;
		});
	}

	/**
	 * Interrupts pending tasks and rejects new ones. Worker threads are virtual (daemon),
	 * so an unresponsive task never blocks JVM exit either way.
	 */
	@Override
	public void close() {
		executor.shutdownNow();
	}

	private static Duration elapsed(long startNanos) {
		return Duration.ofNanos(System.nanoTime() - startNanos);
	}
}
