package com.druvu.lib.fx.prefs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * A small, human-editable key/value preference store backed by a {@code .properties} file - the same
 * plain-text spirit as JConsole Booster's config files, no JSON or database. Values are written
 * through to disk on every mutation, so a set value is a saved value.
 *
 * <p>Not thread-safe: use from a single thread (typically the JavaFX thread).
 */
public final class Prefs {

	private final Path file;
	private final Properties properties = new Properties();

	/**
	 * Opens (and loads, if present) a preference file at the given path.
	 *
	 * @param file the {@code .properties} file to back this store
	 */
	public Prefs(Path file) {
		this.file = Objects.requireNonNull(file, "file");
		load();
	}

	/** Opens {@code preferences.properties} inside the given app home. */
	public static Prefs in(AppHome home) {
		return new Prefs(home.file("preferences.properties"));
	}

	public String get(String key, String defaultValue) {
		return properties.getProperty(key, defaultValue);
	}

	public void put(String key, String value) {
		properties.setProperty(key, value);
		save();
	}

	public int getInt(String key, int defaultValue) {
		return parseOr(properties.getProperty(key), defaultValue, Integer::parseInt);
	}

	public void putInt(String key, int value) {
		put(key, Integer.toString(value));
	}

	public double getDouble(String key, double defaultValue) {
		return parseOr(properties.getProperty(key), defaultValue, Double::parseDouble);
	}

	public void putDouble(String key, double value) {
		put(key, Double.toString(value));
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		final String value = properties.getProperty(key);
		return value == null ? defaultValue : Boolean.parseBoolean(value);
	}

	public void putBoolean(String key, boolean value) {
		put(key, Boolean.toString(value));
	}

	public boolean contains(String key) {
		return properties.containsKey(key);
	}

	public void remove(String key) {
		if (properties.remove(key) != null) {
			save();
		}
	}

	private interface Parser<T> {
		T parse(String s);
	}

	private static <T> T parseOr(String value, T defaultValue, Parser<T> parser) {
		if (value == null) {
			return defaultValue;
		}
		try {
			return parser.parse(value.trim());
		} catch (NumberFormatException malformed) {
			return defaultValue;
		}
	}

	private void load() {
		if (!Files.exists(file)) {
			return;
		}
		try (var reader = Files.newBufferedReader(file)) {
			properties.load(reader);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read preferences: " + file, e);
		}
	}

	private void save() {
		try {
			if (file.getParent() != null) {
				Files.createDirectories(file.getParent());
			}
			try (var writer = Files.newBufferedWriter(file)) {
				properties.store(writer, "druvu-lib-fx preferences");
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Could not write preferences: " + file, e);
		}
	}
}
