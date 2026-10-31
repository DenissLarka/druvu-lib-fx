package com.druvu.lib.fx.updater;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * One downloadable file of a {@link Release}: the installer package for one operating system.
 *
 * @param name the file name as published, for example {@code Letterblade.msix}
 * @param url where to download it from
 * @param size its size in bytes, or {@code -1} when the source does not say
 * @param sha256 the SHA-256 the source publishes for it, as lower-case hex, or empty when it publishes none. An asset
 *     without a digest is never installed: without one the updater cannot tell a tampered download from a good one.
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public record Asset(String name, URI url, long size, String sha256) {

    public Asset {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(sha256, "sha256");
        sha256 = sha256.toLowerCase(Locale.ROOT);
    }

    /** @return whether the source published a SHA-256 for this asset, the precondition of an install */
    public boolean verifiable() {
        return !sha256.isEmpty();
    }
}
