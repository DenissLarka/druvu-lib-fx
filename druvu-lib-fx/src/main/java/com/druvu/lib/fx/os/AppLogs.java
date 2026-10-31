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
import java.util.Objects;
import java.util.Optional;

/**
 * Sends the process's standard output and error into a daily file under the app home's {@code logs} folder, so an
 * installed application - which has no console - keeps a trace of what it printed, the updater's lines included.
 *
 * <p>Call it first thing in {@code main}, before anything logs; {@link #redirectIfInstalled} leaves a development run
 * on the console. The file is {@code <app>-<date>.log}, appended to, closed at shutdown.
 *
 * @author Deniss Larka <br>
 *     on 23 Jul 2023
 */
public final class AppLogs {

    private static final String LOGS_FOLDER = "logs";

    private AppLogs() {}

    /**
     * Redirects only when this is an installed copy ({@link Installation#detect()}), the one case without a console.
     *
     * @param home the app home; the file goes to its {@code logs} folder
     * @return the log file, when redirected
     */
    public static Optional<Path> redirectIfInstalled(AppHome home) {
        if (Installation.detect().isEmpty()) {
            return Optional.empty();
        }
        return redirect(home);
    }

    /**
     * Redirects now, whatever the run.
     *
     * @param home the app home; the file goes to its {@code logs} folder
     * @return the log file, or empty when it could not be opened - the console then stays as it is and the reason is
     *     printed there
     */
    public static Optional<Path> redirect(AppHome home) {
        Objects.requireNonNull(home, "home");
        Path folder = home.dir(LOGS_FOLDER);
        Path appFolder = home.root().getFileName();
        String app = appFolder == null ? "app" : appFolder.toString();
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
}
