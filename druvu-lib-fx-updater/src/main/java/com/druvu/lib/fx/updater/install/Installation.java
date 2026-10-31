package com.druvu.lib.fx.updater.install;

import com.druvu.lib.fx.updater.Installed;
import java.io.IOException;
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The installed copy of the running application and the operating system's way of replacing it.
 *
 * <p>Three kinds exist, one per druvu installer: the AppImage file (Linux), the application bundle (macOS) and the MSIX
 * package (Windows). {@link #detect()} tells which one runs; a development run is none of them and gets no updater. A
 * test supplies its own implementation.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public interface Installation {

    /**
     * Recognises the installed copy from the running process: the {@code APPIMAGE} variable the AppImage runtime sets,
     * the {@code .app} bundle around the Java runtime on macOS, the {@code WindowsApps} folder the runtime lives in on
     * Windows.
     *
     * @return the installation, or empty when this is not an installed copy
     */
    static Optional<Installation> detect() {
        return Installations.detect(Environment.current());
    }

    /**
     * @return the file-name ending of this kind's installer package: {@code .AppImage}, {@code .dmg} or {@code .msix}
     */
    String assetSuffix();

    /** @return where the installed copy lives: the AppImage file, the bundle, the package folder */
    Path location();

    /**
     * Hands a downloaded and verified package to the operating system.
     *
     * @param download the package file
     * @param version the version it contains
     * @return what that left behind, which differs by kind
     * @throws IOException when the operating system refused; the message is shown to the user
     */
    Installed apply(Path download, Version version) throws IOException;

    /**
     * Arranges for the new version to start once this process has exited: a detached helper waits for the exit, then
     * launches the installed copy. The caller quits the application right after.
     *
     * @throws IOException when the helper could not be started
     */
    void relaunch() throws IOException;
}
