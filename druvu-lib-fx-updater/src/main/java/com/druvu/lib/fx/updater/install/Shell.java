package com.druvu.lib.fx.updater.install;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Runs the operating system's command-line tools the installations rely on. */
final class Shell {

    private Shell() {}

    /**
     * Runs a command to completion and returns what it printed.
     *
     * @throws IOException when it could not start or exited non-zero; the message carries its output
     */
    static String run(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try {
            // Drain before waiting: a child that fills the pipe blocks on its write while we would block in waitFor.
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException(command.get(0) + " failed (exit " + exitCode + "): " + output.strip());
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroy();
            throw new IOException("Interrupted while running " + command.get(0), e);
        }
    }

    /**
     * Starts a command that outlives this process: nothing is read from it or written to it, so the JVM can exit while
     * it runs.
     */
    static void startDetached(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectOutput(Redirect.DISCARD);
        builder.redirectError(Redirect.DISCARD);
        Process process = builder.start();
        process.getOutputStream().close();
    }

    /** A literal word for {@code sh}: single quotes, a quote inside written as {@code '\''}. */
    static String singleQuoted(String text) {
        return "'" + text.replace("'", "'\\''") + "'";
    }

    /** A literal string for PowerShell: single quotes, a quote inside written twice, so {@code $} stays text. */
    static String powerShellQuoted(String text) {
        return "'" + text.replace("'", "''") + "'";
    }
}
