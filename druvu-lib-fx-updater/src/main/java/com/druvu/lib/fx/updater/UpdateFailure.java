package com.druvu.lib.fx.updater;

import java.util.Objects;

/**
 * Why an update did not happen, worded for the user. The stack trace is in the log, not here.
 *
 * @param step where it stopped
 * @param message what went wrong, one sentence
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public record UpdateFailure(Step step, String message) {

    public UpdateFailure {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(message, "message");
    }

    /** The steps of an update, in order. */
    public enum Step {
        /** Asking the source for the newest release. */
        CHECK,
        /** Downloading the asset. */
        DOWNLOAD,
        /** Comparing the download with the published SHA-256. */
        VERIFY,
        /** Handing the package to the operating system. */
        APPLY,
        /** Starting the new version. */
        RELAUNCH
    }
}
