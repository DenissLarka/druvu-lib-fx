package com.druvu.lib.fx.updater.install;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What {@link Installations#detect} looks at, taken from the running process or handed in by a test.
 *
 * @param osName the {@code os.name} system property
 * @param variables the environment variables
 * @param javaHome the running Java runtime's home, inside the installed copy when there is one
 * @param pid this process, for a relaunch helper to wait on
 */
record Environment(String osName, Map<String, String> variables, Path javaHome, long pid) {

    Environment {
        Objects.requireNonNull(osName, "osName");
        variables = Map.copyOf(variables);
        Objects.requireNonNull(javaHome, "javaHome");
    }

    static Environment current() {
        return new Environment(
                System.getProperty("os.name", ""),
                System.getenv(),
                Path.of(System.getProperty("java.home")),
                ProcessHandle.current().pid());
    }

    Optional<String> variable(String name) {
        String value = variables.get(name);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    boolean isMac() {
        return osName.toLowerCase(Locale.ROOT).contains("mac");
    }

    boolean isWindows() {
        return osName.toLowerCase(Locale.ROOT).contains("win");
    }
}
