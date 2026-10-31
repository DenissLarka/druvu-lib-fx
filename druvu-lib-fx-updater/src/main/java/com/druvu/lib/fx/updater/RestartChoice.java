package com.druvu.lib.fx.updater;

/**
 * The user's answer once an update is applied and a restart would run it.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public enum RestartChoice {
    /** Start the new version now: the updater arranges the relaunch, the host quits the application. */
    RESTART_NOW,
    /** Keep working; the next start runs the new version. */
    LATER
}
