package com.druvu.lib.fx.os;

import com.druvu.lib.fx.prefs.AppHome;
import com.druvu.lib.fx.updater.install.Installation;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sets up an application's logging in one call at the top of {@code main}: the JDK's own backend behind
 * {@link System.Logger}, one line per record, the level from {@code -Ddruvu.log} on druvu's own loggers; and, for an
 * installed copy - which has no console - the process's output in a daily file under the app home's {@code logs}
 * folder, the updater's lines included.
 *
 * <p>The kit and its updater log through {@code System.Logger}, and so can the app: no logging dependency is needed.
 * The backend is {@code java.util.logging}, which the kit requires, so every runtime image built on the kit carries it.
 * A dependency that logs through another API brings its own bridge to that backend ({@code slf4j-jdk14} for SLF4J,
 * {@code log4j-to-jul} for the Log4j API).
 *
 * <p>A run that configures the backend itself ({@code java.util.logging.config.file} or {@code .config.class}) keeps
 * its configuration; only the file is set up then. Call it before anything logs: the handler writes to the error stream
 * as it is at that moment.
 *
 * @author Deniss Larka <br>
 *     on 23 Jul 2023
 */
public final class AppLogs {

    /**
     * The level: a {@link System.Logger.Level} name, {@code INFO} when absent. Below {@code INFO} it opens up druvu's
     * own loggers ({@code com.druvu}) only; the others stay at {@code INFO}.
     */
    public static final String LEVEL_PROPERTY = "druvu.log";

    /** {@code true} sends the output to the file in a development run too. */
    public static final String FILE_PROPERTY = "druvu.log.file";

    private static final String LOGS_FOLDER = "logs";
    private static final Level DEFAULT_LEVEL = Level.INFO;

    // The level opens up druvu's own loggers only: at FINE the JDK's would list every trusted CA as TLS
    // starts. Held in a field because the JDK keeps loggers weakly, and a collected logger forgets its level.
    private static final Logger DRUVU = Logger.getLogger("com.druvu");

    private AppLogs() {}

    /**
     * Sets up the backend and, for an installed copy ({@link Installation#detect()}) or with
     * {@code -Ddruvu.log.file=true}, the daily file {@code <app>-<date>.log} under the app home's {@code logs} folder.
     *
     * @param home the app home
     * @return the log file, when the output goes to one; empty for a development run, and when the file could not be
     *     opened - the console then stays as it is and the reason is printed there
     */
    public static Optional<Path> setUp(AppHome home) {
        Objects.requireNonNull(home, "home");
        boolean toFile = Installation.detect().isPresent() || Boolean.getBoolean(FILE_PROPERTY);
        Optional<Path> file = toFile ? redirect(home.dir(LOGS_FOLDER), appName(home)) : Optional.empty();
        if (!backendConfiguredByTheRun()) {
            configureBackend(level(System.getProperty(LEVEL_PROPERTY)));
        }
        return file;
    }

    /**
     * Sends the process's standard output and error to {@code <folder>/<app>-<date>.log}; appends, closes at shutdown.
     */
    static Optional<Path> redirect(Path folder, String app) {
        Path file = folder.resolve(app + "-" + LocalDate.now() + ".log");
        try {
            OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            PrintStream stream = new PrintStream(out, true, StandardCharsets.UTF_8);
            System.setOut(stream);
            System.setErr(stream);
            Runtime.getRuntime().addShutdownHook(new Thread(stream::close, "druvu-app-logs"));
            return Optional.of(file);
        } catch (IOException e) {
            System.err.println("Cannot open the log file " + file + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * One handler on the root logger at the given level, writing one line per record to the error stream as it is now:
     * the file after {@link #redirect}, the console otherwise. The root stays at {@code INFO} and druvu's own loggers
     * take the level, so a {@code DEBUG} run shows the app's details without the JDK's.
     */
    static void configureBackend(Level level) {
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler); // the JDK's default handler prints two lines per record
        }
        Handler console = new ConsoleHandler();
        console.setFormatter(new LogLine());
        console.setLevel(level);
        root.addHandler(console);
        root.setLevel(Level.INFO);
        DRUVU.setLevel(level);
    }

    /**
     * The backend level for a {@link System.Logger.Level} name; {@code INFO} for none, and for a name that is not one.
     */
    static Level level(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT_LEVEL;
        }
        try {
            return toBackend(System.Logger.Level.valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown log level '" + name + "' in -D" + LEVEL_PROPERTY + "; using INFO");
            return DEFAULT_LEVEL;
        }
    }

    private static String appName(AppHome home) {
        Path folder = home.root().getFileName();
        return folder == null ? "app" : folder.toString();
    }

    private static boolean backendConfiguredByTheRun() {
        return System.getProperty("java.util.logging.config.file") != null
                || System.getProperty("java.util.logging.config.class") != null;
    }

    // The mapping the JDK applies when a System.Logger record reaches java.util.logging.
    private static Level toBackend(System.Logger.Level level) {
        return switch (level) {
            case ALL -> Level.ALL;
            case TRACE -> Level.FINER;
            case DEBUG -> Level.FINE;
            case INFO -> Level.INFO;
            case WARNING -> Level.WARNING;
            case ERROR -> Level.SEVERE;
            case OFF -> Level.OFF;
        };
    }
}
