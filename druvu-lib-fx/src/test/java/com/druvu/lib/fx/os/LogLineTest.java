package com.druvu.lib.fx.os;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.testng.annotations.Test;

/** Covers LogLine: the one-line shape with the message parameters filled in, the level names, the stack trace. */
public class LogLineTest {

    private static final String TIME = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}";

    @Test
    public void oneLinePerRecord() {
        LogRecord record = new LogRecord(Level.INFO, "Checking {0} for updates");
        record.setLoggerName("com.druvu.lib.fx.update.Updates");
        record.setParameters(new Object[] {"Letterblade"});

        String line = new LogLine().format(record);

        assertThat(line)
                .matches(TIME + " INFO    com\\.druvu\\.lib\\.fx\\.update\\.Updates - Checking Letterblade for updates"
                        + System.lineSeparator());
    }

    @Test
    public void levelsShowUnderTheSystemLoggerNames() {
        assertThat(LogLine.levelName(Level.SEVERE)).isEqualTo("ERROR");
        assertThat(LogLine.levelName(Level.WARNING)).isEqualTo("WARNING");
        assertThat(LogLine.levelName(Level.INFO)).isEqualTo("INFO");
        assertThat(LogLine.levelName(Level.FINE)).isEqualTo("DEBUG");
        assertThat(LogLine.levelName(Level.FINER)).isEqualTo("TRACE");
        assertThat(LogLine.levelName(Level.CONFIG)).isEqualTo("CONFIG");
    }

    @Test
    public void stackTraceUnderTheLine() {
        LogRecord record = new LogRecord(Level.SEVERE, "The check failed");
        record.setLoggerName("updates");
        record.setThrown(new IllegalStateException("boom"));

        String text = new LogLine().format(record);

        assertThat(text)
                .contains("ERROR   updates - The check failed" + System.lineSeparator()
                        + "java.lang.IllegalStateException: boom")
                .contains("\tat com.druvu.lib.fx.os.LogLineTest.stackTraceUnderTheLine(");
    }
}
