package com.druvu.lib.fx.updater;

import java.lang.module.ModuleDescriptor.Version;

/**
 * How a check ended. The host has already seen everything the user needed to see; the outcome is for the caller's log
 * and for tests.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public sealed interface Outcome {

    /** The user switched automatic checks off; nothing was asked of the source. */
    record Disabled() implements Outcome {}

    /**
     * The newest release is not newer than the running version.
     *
     * @param current the running version
     */
    record UpToDate(Version current) implements Outcome {}

    /**
     * A newer release exists, and the user had asked never to be offered this one.
     *
     * @param version the version left alone
     */
    record Skipped(Version version) implements Outcome {}

    /**
     * The user was offered a newer release and said no.
     *
     * @param version the version offered
     * @param decision {@link Decision#NOT_NOW} or {@link Decision#SKIP_VERSION}
     */
    record Declined(Version version, Decision decision) implements Outcome {}

    /**
     * The update was downloaded, verified and handed to the operating system.
     *
     * @param installed what that left behind, by operating system
     * @param choice whether the user restarts now; on {@link RestartChoice#RESTART_NOW} the relaunch is already
     *     arranged and the host quits the application
     */
    record Applied(Installed installed, RestartChoice choice) implements Outcome {}

    /**
     * The check or the update stopped.
     *
     * @param failure where and why
     */
    record Failed(UpdateFailure failure) implements Outcome {}
}
