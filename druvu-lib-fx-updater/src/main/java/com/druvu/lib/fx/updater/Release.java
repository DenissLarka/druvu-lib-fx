package com.druvu.lib.fx.updater;

import java.lang.module.ModuleDescriptor.Version;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A published release of the application, as an {@link UpdateSource} reports it.
 *
 * @param version the released version
 * @param page where a person reads about it, the release page
 * @param notes the release notes, as published; empty when there are none
 * @param assets the downloadable files, one installer package per operating system
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public record Release(Version version, URI page, String notes, List<Asset> assets) {

    public Release {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(notes, "notes");
        assets = List.copyOf(assets);
    }

    /**
     * The asset for one operating system, found by its file name's ending.
     *
     * @param suffix the ending an {@link com.druvu.lib.fx.updater.install.Installation} expects, such as {@code .msix};
     *     compared without regard to case
     * @return the first asset whose name ends with it
     */
    public Optional<Asset> asset(String suffix) {
        Objects.requireNonNull(suffix, "suffix");
        return assets.stream()
                .filter(asset -> endsWithIgnoringCase(asset.name(), suffix))
                .findFirst();
    }

    private static boolean endsWithIgnoringCase(String name, String suffix) {
        int start = name.length() - suffix.length();
        return start >= 0 && name.regionMatches(true, start, suffix, 0, suffix.length());
    }
}
