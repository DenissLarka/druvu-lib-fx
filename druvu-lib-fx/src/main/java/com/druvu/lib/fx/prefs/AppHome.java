package com.druvu.lib.fx.prefs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Resolves a per-application directory for user data (preferences, logs, bookmarks, ...). By default
 * this is {@code ~/.druvu.com/<appName>}, so every druvu app shares the {@code ~/.druvu.com} parent -
 * the same convention JConsole Booster established. An optional environment variable relocates it
 * (e.g. to a synced folder), matching JCB's {@code JCONSOLE_BOOSTER_HOME}.
 *
 * <p>The directory (and any subdirectory obtained via {@link #dir(String)}) is created on demand.
 */
public final class AppHome {

	private static final String VENDOR_DIR = ".druvu.com";

	private final Path root;

	private AppHome(Path root) {
		this.root = ensureDir(root);
	}

	/**
	 * @param appName folder name under {@code ~/.druvu.com} (e.g. {@code "market-watch"})
	 * @return the app home at {@code ~/.druvu.com/<appName>}
	 */
	public static AppHome of(String appName) {
		return of(appName, null);
	}

	/**
	 * @param appName folder name under {@code ~/.druvu.com}
	 * @param envVar  name of an environment variable that, if set and non-blank, overrides the whole
	 *                path (like JCB's {@code JCONSOLE_BOOSTER_HOME}); {@code null} to disable override
	 * @return the resolved app home, directory created
	 */
	public static AppHome of(String appName, String envVar) {
		Objects.requireNonNull(appName, "appName");
		final String override = envVar == null ? null : System.getenv(envVar);
		return new AppHome(computeRoot(override, System.getProperty("user.home"), appName));
	}

	/** @return the app home directory (created). */
	public Path root() {
		return root;
	}

	/** @return {@code root/name}, not created (for a file). */
	public Path file(String name) {
		return root.resolve(name);
	}

	/** @return {@code root/name}, created as a directory. */
	public Path dir(String name) {
		return ensureDir(root.resolve(name));
	}

	static Path computeRoot(String envValue, String userHome, String appName) {
		if (envValue != null && !envValue.isBlank()) {
			return Paths.get(envValue);
		}
		return Paths.get(userHome, VENDOR_DIR, appName);
	}

	static Path ensureDir(Path dir) {
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create app directory: " + dir, e);
		}
		return dir;
	}
}
