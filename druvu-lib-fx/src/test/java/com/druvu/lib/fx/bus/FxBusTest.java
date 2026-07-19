package com.druvu.lib.fx.bus;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.lib.fx.FxTestToolkit;
import com.druvu.lib.fx.util.FxThreads;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class FxBusTest {

    private record Tick(String symbol, double price) {}

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    @Test
    public void testCallerDeliveryIsSynchronousOnPublishingThread() {
        final FxBus bus = new FxBus();
        final List<String> seen = new CopyOnWriteArrayList<>();
        final Thread publisher = Thread.currentThread();
        final AtomicBoolean sameThread = new AtomicBoolean(false);

        try (Subscription sub = bus.subscribe(Tick.class, Delivery.CALLER, tick -> {
            sameThread.set(Thread.currentThread() == publisher);
            seen.add(tick.symbol());
        })) {
            assertThat(sub).isNotNull();
            bus.publish(new Tick("EURUSD", 1.08));
        }
        assertThat(seen).containsExactly("EURUSD");
        assertThat(sameThread).isTrue();
    }

    @Test
    public void testFxDeliveryLandsOnFxThread() throws InterruptedException {
        final FxBus bus = new FxBus();
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicBoolean wasOnFx = new AtomicBoolean(false);

        try (Subscription sub = bus.subscribe(Tick.class, Delivery.FX, tick -> {
            wasOnFx.set(FxThreads.isFx());
            done.countDown();
        })) {
            assertThat(sub).isNotNull();
            bus.publish(new Tick("USDJPY", 155.10));
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(wasOnFx).isTrue();
    }

    @Test
    public void testAsyncDeliveryRunsOnVirtualNonFxThread() throws InterruptedException {
        final FxBus bus = new FxBus();
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicBoolean virtualNonFx = new AtomicBoolean(false);

        try (Subscription sub = bus.subscribe(Tick.class, Delivery.ASYNC, tick -> {
            virtualNonFx.set(Thread.currentThread().isVirtual() && !FxThreads.isFx());
            done.countDown();
        })) {
            assertThat(sub).isNotNull();
            bus.publish(new Tick("XAUUSD", 2380.0));
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(virtualNonFx).isTrue();
    }

    @Test
    public void testPolymorphicDispatchAndTypeFiltering() {
        final FxBus bus = new FxBus();
        final AtomicInteger asSupertype = new AtomicInteger();
        final AtomicInteger asString = new AtomicInteger();

        try (Subscription all = bus.subscribe(Object.class, Delivery.CALLER, e -> asSupertype.incrementAndGet());
                Subscription strings = bus.subscribe(String.class, Delivery.CALLER, s -> asString.incrementAndGet())) {
            assertThat(all).isNotNull();
            assertThat(strings).isNotNull();
            bus.publish(new Tick("EURUSD", 1.08));
            bus.publish("plain event");
        }
        assertThat(asSupertype.get()).isEqualTo(2);
        assertThat(asString.get()).isEqualTo(1);
    }

    @Test
    public void testClosedSubscriptionReceivesNothing() {
        final FxBus bus = new FxBus();
        final AtomicInteger count = new AtomicInteger();

        final Subscription sub = bus.subscribe(Tick.class, Delivery.CALLER, tick -> count.incrementAndGet());
        bus.publish(new Tick("EURUSD", 1.08));
        sub.close();
        sub.close();
        bus.publish(new Tick("EURUSD", 1.09));

        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    public void testThrowingSubscriberDoesNotBreakOthersOrPublisher() {
        final FxBus bus = new FxBus();
        final AtomicInteger healthy = new AtomicInteger();

        try (Subscription bad = bus.subscribe(Tick.class, Delivery.CALLER, tick -> {
                    throw new IllegalStateException("boom");
                });
                Subscription good = bus.subscribe(Tick.class, Delivery.CALLER, tick -> healthy.incrementAndGet())) {
            assertThat(bad).isNotNull();
            assertThat(good).isNotNull();
            bus.publish(new Tick("EURUSD", 1.08));
        }
        assertThat(healthy.get()).isEqualTo(1);
    }
}
