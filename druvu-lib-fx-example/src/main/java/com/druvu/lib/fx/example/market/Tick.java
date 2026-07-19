package com.druvu.lib.fx.example.market;

import java.time.Instant;

/** A market data tick - the event streamed on the bus by {@link MarketFeed}. */
public record Tick(String symbol, double price, long qty, Instant at) {}
