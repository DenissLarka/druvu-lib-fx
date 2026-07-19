package com.druvu.lib.fx.prefs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

public class PrefsTest {

    @Test
    public void writesThroughAndReloadsTypedValues() throws IOException {
        final Path file = Files.createTempFile("druvu-prefs", ".properties");
        Files.delete(file); // start from absent, as a fresh install would
        try {
            final Prefs prefs = new Prefs(file);
            prefs.put("app", "market-watch");
            prefs.putInt("count", 42);
            prefs.putDouble("ratio", 0.65);
            prefs.putBoolean("dark", true);

            assertThat(Files.exists(file)).isTrue(); // write-through persisted immediately

            final Prefs reloaded = new Prefs(file); // a fresh store over the same file
            assertThat(reloaded.get("app", "")).isEqualTo("market-watch");
            assertThat(reloaded.getInt("count", 0)).isEqualTo(42);
            assertThat(reloaded.getDouble("ratio", 0)).isEqualTo(0.65);
            assertThat(reloaded.getBoolean("dark", false)).isTrue();
            assertThat(reloaded.contains("app")).isTrue();
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void returnsDefaultsForMissingOrMalformedAndRemoves() throws IOException {
        final Path file = Files.createTempFile("druvu-prefs", ".properties");
        try {
            final Prefs prefs = new Prefs(file);
            assertThat(prefs.get("missing", "fallback")).isEqualTo("fallback");
            assertThat(prefs.getInt("missing", 7)).isEqualTo(7);

            prefs.put("bad", "not-a-number");
            assertThat(prefs.getInt("bad", 7)).isEqualTo(7); // malformed -> default
            assertThat(prefs.getDouble("bad", 1.5)).isEqualTo(1.5);

            prefs.remove("bad");
            assertThat(prefs.contains("bad")).isFalse();
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
