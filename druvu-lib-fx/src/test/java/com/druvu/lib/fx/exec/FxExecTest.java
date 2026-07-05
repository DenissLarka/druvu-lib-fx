package com.druvu.lib.fx.exec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.druvu.lib.fx.FxTestToolkit;
import com.druvu.lib.fx.bus.Delivery;
import com.druvu.lib.fx.bus.FxBus;
import com.druvu.lib.fx.bus.Subscription;
import com.druvu.lib.fx.util.FxThreads;

public class FxExecTest {

	@BeforeClass
	public void startToolkit() {
		FxTestToolkit.ensureStarted();
	}

	@Test
	public void testSupplyRunsOffFxOnVirtualThreadAndReturnsValue() throws Exception {
		try (FxExec exec = new FxExec()) {
			final AtomicBoolean virtualNonFx = new AtomicBoolean(false);
			final CompletableFuture<Integer> future = exec.supply("answer", () -> {
				FxThreads.requireOffFx();
				virtualNonFx.set(Thread.currentThread().isVirtual());
				return 42;
			});
			assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(42);
			assertThat(virtualNonFx).isTrue();
		}
	}

	@Test
	public void testLifecycleEventsStartedThenFinished() throws Exception {
		final FxBus bus = new FxBus();
		final List<TaskEvent> events = new CopyOnWriteArrayList<>();
		try (Subscription sub = bus.subscribe(TaskEvent.class, Delivery.CALLER, events::add);
			 FxExec exec = new FxExec(bus)) {
			assertThat(sub).isNotNull();
			exec.run("load-portfolio", () -> {
			}).get(10, TimeUnit.SECONDS);
		}
		assertThat(events).hasSize(2);
		assertThat(events.get(0)).isInstanceOf(TaskEvent.Started.class);
		assertThat(events.get(1)).isInstanceOf(TaskEvent.Finished.class);
		assertThat(events.get(0).id()).isEqualTo(events.get(1).id());
		assertThat(events.get(0).title()).isEqualTo("load-portfolio");
	}

	@Test
	public void testFailurePublishesFailedAndCompletesExceptionally() throws InterruptedException {
		final FxBus bus = new FxBus();
		final List<TaskEvent> events = new CopyOnWriteArrayList<>();
		final IllegalStateException boom = new IllegalStateException("boom");
		try (Subscription sub = bus.subscribe(TaskEvent.class, Delivery.CALLER, events::add);
			 FxExec exec = new FxExec(bus)) {
			assertThat(sub).isNotNull();
			final CompletableFuture<Void> future = exec.run("explode", () -> {
				throw boom;
			});
			assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
					.isInstanceOf(ExecutionException.class)
					.hasCause(boom);
		}
		assertThat(events).hasSize(2);
		assertThat(events.get(1)).isInstanceOf(TaskEvent.Failed.class);
		assertThat(((TaskEvent.Failed) events.get(1)).error()).isSameAs(boom);
	}

	@Test
	public void testContinuationHopsToFxThreadViaFxExecutor() throws Exception {
		try (FxExec exec = new FxExec()) {
			final CountDownLatch done = new CountDownLatch(1);
			final AtomicBoolean continuedOnFx = new AtomicBoolean(false);
			exec.supply("fetch", () -> "value")
					.thenAcceptAsync(v -> {
						continuedOnFx.set(FxThreads.isFx());
						done.countDown();
					}, FxThreads.fxExecutor());
			assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
			assertThat(continuedOnFx).isTrue();
		}
	}
}
