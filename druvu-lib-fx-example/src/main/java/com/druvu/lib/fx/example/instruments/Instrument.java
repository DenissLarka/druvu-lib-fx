package com.druvu.lib.fx.example.instruments;

import java.time.LocalDate;
import java.util.List;

public record Instrument(String symbol, String name, AssetClass assetClass, double price,
						 LocalDate listed, boolean favourite) {

	/**
	 * Demo data set, "loaded" by the controller through FxExec with simulated latency.
	 */
	public static List<Instrument> demoData() {
		return List.of(
				new Instrument("EURUSD", "Euro / US Dollar", AssetClass.FX, 1.085, LocalDate.of(1999, 1, 4), true),
				new Instrument("USDJPY", "US Dollar / Japanese Yen", AssetClass.FX, 155.2, LocalDate.of(1971, 8, 16), true),
				new Instrument("GBPUSD", "British Pound / US Dollar", AssetClass.FX, 1.272, LocalDate.of(1971, 8, 16), false),
				new Instrument("USDCHF", "US Dollar / Swiss Franc", AssetClass.FX, 0.897, LocalDate.of(1971, 8, 16), false),
				new Instrument("AUDUSD", "Australian Dollar / US Dollar", AssetClass.FX, 0.664, LocalDate.of(1983, 12, 12), false),
				new Instrument("XAUUSD", "Gold Spot", AssetClass.METALS, 2382.0, LocalDate.of(1974, 12, 31), true),
				new Instrument("XAGUSD", "Silver Spot", AssetClass.METALS, 28.4, LocalDate.of(1975, 1, 2), false),
				new Instrument("XPTUSD", "Platinum Spot", AssetClass.METALS, 991.5, LocalDate.of(1987, 3, 2), false),
				new Instrument("CL.1", "WTI Crude Oil Front Month", AssetClass.ENERGY, 78.2, LocalDate.of(1983, 3, 30), false),
				new Instrument("CO.1", "Brent Crude Front Month", AssetClass.ENERGY, 82.5, LocalDate.of(1988, 6, 23), false),
				new Instrument("NG.1", "Henry Hub Natural Gas", AssetClass.ENERGY, 2.87, LocalDate.of(1990, 4, 3), false),
				new Instrument("SR3Z6", "3M SOFR Future Dec-26", AssetClass.RATES, 95.9, LocalDate.of(2018, 5, 7), true),
				new Instrument("FGBL", "Euro-Bund Future", AssetClass.RATES, 131.8, LocalDate.of(1990, 11, 23), false),
				new Instrument("ZN", "10Y US T-Note Future", AssetClass.RATES, 110.3, LocalDate.of(1982, 5, 3), false));
	}
}
