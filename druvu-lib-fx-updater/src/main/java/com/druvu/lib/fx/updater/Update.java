package com.druvu.lib.fx.updater;

import java.lang.module.ModuleDescriptor.Version;
import java.net.URI;
import java.util.Objects;

/**
 * A newer release offered to the user, narrowed to the one asset this installation can apply.
 *
 * @param version the version on offer
 * @param notes its release notes, empty when there are none
 * @param page the release page, for a "read more" link
 * @param asset the installer package for this operating system
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public record Update(Version version, String notes, URI page, Asset asset) {

    public Update {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(notes, "notes");
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(asset, "asset");
    }
}
