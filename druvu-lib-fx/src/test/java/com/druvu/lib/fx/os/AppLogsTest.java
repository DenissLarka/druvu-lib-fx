package com.druvu.lib.fx.os;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Covers AppLogs: the level from the System.Logger names, the daily file under the logs folder, and the backend writing
 * one line per record into it at the configured level. The process streams, the root logger and druvu's are restored
 * after each test; the file's stream stays open until the shutdown hook closes it, like in an app.
 */
public class AppLogsTest {

    private static final Logger DRUVU = Logger.getLogger("com.druvu");

    private PrintStream out;
    private PrintStream err;
    private Handler[] rootHandlers;
    private Level rootLevel;
    private Level druvuLevel;

    @BeforeMethod
    public void keepTheProcessState() {
        out = System.out;
        err = System.err;
        Logger root = Logger.getLogger("");
        rootHandlers = root.getHandlers();
        rootLevel = root.getLevel();
        druvuLevel = DRUVU.getLevel();
    }

    @AfterMethod
    public void restoreTheProcessState() {
        System.setOut(out);
        System.setErr(err);
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        for (Handler handler : rootHandlers) {
            root.addHandler(handler);
        }
        root.setLevel(rootLevel);
        DRUVU.setLevel(druvuLevel);
    }

    @Test
    public void levelFromTheSystemLoggerNames() {
        assertThat(AppLogs.level(null)).isEqualTo(Level.INFO);
        assertThat(AppLogs.level(" ")).isEqualTo(Level.INFO);
        assertThat(AppLogs.level("debug")).isEqualTo(Level.FINE);
        assertThat(AppLogs.level("TRACE")).isEqualTo(Level.FINER);
        assertThat(AppLogs.level("Warning")).isEqualTo(Level.WARNING);
        assertThat(AppLogs.level("error")).isEqualTo(Level.SEVERE);
        assertThat(AppLogs.level("off")).isEqualTo(Level.OFF);
    }

    @Test
    public void unknownLevelFallsBackToInfo() {
        assertThat(AppLogs.level("loud")).isEqualTo(Level.INFO);
    }

    @Test
    public void recordsLandInTheDailyFileAtTheConfiguredLevel() throws IOException {
        Path folder = Files.createTempDirectory("app-logs");

        Optional<Path> file = AppLogs.redirect(folder, "hello");
        AppLogs.configureBackend(Level.INFO);
        System.Logger logger = System.getLogger("com.druvu.hello");
        logger.log(System.Logger.Level.INFO, "Opened {0} messages", 3);
        logger.log(System.Logger.Level.DEBUG, "not at INFO");
        System.out.println("printed, not logged");

        assertThat(file).isPresent();
        assertThat(file.get()).hasParent(folder);
        assertThat(file.get().getFileName().toString()).matches("hello-\\d{4}-\\d{2}-\\d{2}\\.log");
        String text = Files.readString(file.get(), StandardCharsets.UTF_8);
        assertThat(text)
                .contains(" INFO    com.druvu.hello - Opened 3 messages")
                .contains("printed, not logged")
                .doesNotContain("not at INFO");
    }

    @Test
    public void debugLevelLetsDebugRecordsThrough() throws IOException {
        Path folder = Files.createTempDirectory("app-logs");

        Optional<Path> file = AppLogs.redirect(folder, "hello");
        AppLogs.configureBackend(AppLogs.level("debug"));
        System.getLogger("com.druvu.hello").log(System.Logger.Level.DEBUG, "the details");

        assertThat(Files.readString(file.orElseThrow(), StandardCharsets.UTF_8))
                .contains(" DEBUG   com.druvu.hello - the details");
    }

    @Test
    public void debugLevelOpensUpDruvuLoggersOnly() throws IOException {
        Path folder = Files.createTempDirectory("app-logs");

        Optional<Path> file = AppLogs.redirect(folder, "hello");
        AppLogs.configureBackend(AppLogs.level("debug"));
        System.getLogger("com.druvu.hello").log(System.Logger.Level.DEBUG, "the app's details");
        System.Logger library = System.getLogger("org.example.library");
        library.log(System.Logger.Level.DEBUG, "the library's details");
        library.log(System.Logger.Level.INFO, "the library at INFO");

        assertThat(Files.readString(file.orElseThrow(), StandardCharsets.UTF_8))
                .contains("com.druvu.hello - the app's details")
                .contains("org.example.library - the library at INFO")
                .doesNotContain("the library's details");
    }
}
