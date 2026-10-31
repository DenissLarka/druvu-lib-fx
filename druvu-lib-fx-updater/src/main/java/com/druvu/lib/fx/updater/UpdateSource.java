package com.druvu.lib.fx.updater;

import java.io.IOException;

/**
 * Where releases are published. {@link com.druvu.lib.fx.updater.github.GitHubReleases} is the one that ships; a test or
 * a private distribution writes its own.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
@FunctionalInterface
public interface UpdateSource {

    /**
     * The newest published release, whatever version it has: the {@link Updater} compares.
     *
     * @return the release
     * @throws IOException when the source cannot be reached or read; the message is shown to the user as it is
     */
    Release latest() throws IOException;
}
