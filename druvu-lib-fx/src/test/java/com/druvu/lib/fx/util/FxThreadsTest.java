package com.druvu.lib.fx.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.druvu.lib.fx.FxTestToolkit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class FxThreadsTest {

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    @Test
    public void testCallerThreadIsNotFx() {
        assertThat(FxThreads.isFx()).isFalse();
        FxThreads.requireOffFx();
        assertThatIllegalStateException().isThrownBy(FxThreads::requireFx);
    }

    @Test
    public void testOnFxHopsToFxThread() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicBoolean wasOnFx = new AtomicBoolean(false);
        FxThreads.onFx(() -> {
            wasOnFx.set(FxThreads.isFx());
            done.countDown();
        });
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(wasOnFx.get()).isTrue();
    }

    @Test
    public void testFxExecutorRunsOnFxThread() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicBoolean wasOnFx = new AtomicBoolean(false);
        FxThreads.fxExecutor().execute(() -> {
            wasOnFx.set(FxThreads.isFx());
            done.countDown();
        });
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(wasOnFx.get()).isTrue();
    }
}
