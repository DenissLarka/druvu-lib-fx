package com.druvu.lib.fx.updater.fixtures;

import com.druvu.lib.fx.updater.Release;
import com.druvu.lib.fx.updater.UpdateSource;
import java.io.IOException;

/** A source that answers with one release, or fails like an unreachable server. */
public final class CannedSource implements UpdateSource {

    private final Release release;
    private final String failure;
    private int calls;

    private CannedSource(Release release, String failure) {
        this.release = release;
        this.failure = failure;
    }

    public static CannedSource offering(Release release) {
        return new CannedSource(release, null);
    }

    public static CannedSource unreachable(String reason) {
        return new CannedSource(null, reason);
    }

    @Override
    public Release latest() throws IOException {
        calls++;
        if (failure != null) {
            throw new IOException(failure);
        }
        return release;
    }

    public int calls() {
        return calls;
    }
}
