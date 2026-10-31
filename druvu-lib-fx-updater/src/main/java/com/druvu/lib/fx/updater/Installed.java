package com.druvu.lib.fx.updater;

import java.lang.module.ModuleDescriptor.Version;

/**
 * What applying an update left behind, which differs by operating system. The host words its dialog from the case.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public sealed interface Installed {

    /** @return the version that is now installed, or ready to be */
    Version version();

    /** @return whether a restart runs the new version; false when the user still has steps to take by hand */
    default boolean restartable() {
        return !(this instanceof ManualSteps);
    }

    /**
     * Windows: the MSIX package is registered for the next start, because Windows cannot swap the package while the
     * application runs. The user keeps working; the next launch is the new version.
     *
     * @param version the version waiting
     */
    record AtNextStart(Version version) implements Installed {}

    /**
     * Linux: the running AppImage file was replaced on disk. The running process is still the old version; the next
     * launch is the new one.
     *
     * @param version the version on disk now
     */
    record Replaced(Version version) implements Installed {}

    /**
     * macOS: the disk image is mounted and open in Finder; the user drags the new bundle over the old one. Nothing
     * restarts by itself.
     *
     * @param version the version in the disk image
     * @param instructions what the user does next, ready to show
     */
    record ManualSteps(Version version, String instructions) implements Installed {}
}
