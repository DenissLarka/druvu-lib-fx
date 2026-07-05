package com.druvu.lib.fx.example.market;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.druvu.lib.fx.bus.FxBus;
import com.druvu.lib.fx.exec.FxExec;

/**
 * Simulated market data feed: a random walk over a few symbols, published as {@link Tick}s
 * on the bus from a background virtual thread. Runs as a plain FxExec task, so the toolbar's
 * LIVE indicator and the status strip observe it through ordinary TaskEvents - the feed knows
 * nothing about the UI.
 */
public final class MarketFeed {

	public static final String TASK_TITLE = "market feed";

	private static final Map<String, Double> SEEDS = Map.of(
			"EURUSD", 1.085,
			"USDJPY", 155.2,
			"GBPUSD", 1.272,
			"XAUUSD", 2382.0);

	// touched only by the feed's worker thread
	private final Map<String, Double> prices = new HashMap<>(SEEDS);
	private final Random random = new Random();
	private final FxBus bus;
	private final FxExec exec;
	private volatile boolean running;

	public MarketFeed(FxBus bus, FxExec exec) {
		this.bus = bus;
		this.exec = exec;
	}

	public synchronized void start() {
		if (running) {
			return;
		}
		running = true;
		exec.run(TASK_TITLE, this::loop);
	}

	public void stop() {
		running = false;
	}

	private void loop() {
		final List<String> symbols = List.copyOf(prices.keySet());
		try {
			while (running) {
				Thread.sleep(250);
				final String symbol = symbols.get(random.nextInt(symbols.size()));
				final double price = prices.compute(symbol,
						(s, old) -> old * (1 + random.nextGaussian() * 0.0006));
				bus.publish(new Tick(symbol, price, 1 + random.nextInt(100), Instant.now()));
			}
		} catch (InterruptedException ex) {
			// app shutdown interrupts the executor: leave quietly as a Finished task
			Thread.currentThread().interrupt();
		}
	}
}
