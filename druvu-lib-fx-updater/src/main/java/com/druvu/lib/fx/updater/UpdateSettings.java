package com.druvu.lib.fx.updater;

import java.lang.module.ModuleDescriptor.Version;

/**
 * What the user decided for good: whether the application checks at start, and which version never to offer again. The
 * host persists these; the kit keeps them in the application's preferences.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public interface UpdateSettings {

    /** @return whether {@link Updater#checkAtStart()} may check at all; a user-triggered check always may */
    boolean checksAtStart();

    /**
     * @param version a version the source offers
     * @return whether the user asked never to be offered it; an automatic check then stays quiet, a user-triggered
     *     check offers it anyway
     */
    boolean isSkipped(Version version);

    /**
     * Remembers {@link Decision#SKIP_VERSION}. One version at a time is enough: a newer release replaces it.
     *
     * @param version the version never to offer again
     */
    void skip(Version version);
}
