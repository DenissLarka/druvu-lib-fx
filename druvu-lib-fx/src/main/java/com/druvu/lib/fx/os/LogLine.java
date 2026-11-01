package com.druvu.lib.fx.os;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * One record, one line - {@code 2026-10-31 14:02:03.123 INFO com.druvu.lib.fx.update.Updates - Checking for updates} -
 * with the stack trace under it when the record carries one. The level shows under the name {@link System.Logger} uses
 * ({@code DEBUG}, not the backend's {@code FINE}).
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
final class LogLine extends Formatter {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Override
    public String format(LogRecord record) {
        StringBuilder line = new StringBuilder(160)
                .append(TIME.format(record.getInstant()))
                .append(' ')
                .append(String.format("%-7s", levelName(record.getLevel())))
                .append(' ')
                .append(Objects.requireNonNullElse(record.getLoggerName(), ""))
                .append(" - ")
                .append(formatMessage(record))
                .append(System.lineSeparator());
        Throwable thrown = record.getThrown();
        if (thrown != null) {
            StringWriter trace = new StringWriter();
            thrown.printStackTrace(new PrintWriter(trace));
            line.append(trace);
        }
        return line.toString();
    }

    // The JDK maps System.Logger's levels onto the backend's (ERROR to SEVERE, DEBUG to FINE, TRACE to FINER); the line
    // shows the names of the one API the kit and the apps log through. A backend-only level keeps its own name.
    static String levelName(Level level) {
        return switch (level.getName()) {
            case "SEVERE" -> "ERROR";
            case "FINE" -> "DEBUG";
            case "FINER" -> "TRACE";
            default -> level.getName();
        };
    }
}
