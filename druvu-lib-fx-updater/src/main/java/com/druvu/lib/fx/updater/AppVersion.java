package com.druvu.lib.fx.updater;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Version;
import java.util.Objects;
import java.util.Optional;

/**
 * The running application's version, read from its module descriptor.
 *
 * <p>A packaged application runs from a jlink image, where {@code MANIFEST.MF} is gone but the module's version
 * survives in {@code module-info.class}, provided the module was compiled with {@code --module-version} (the
 * {@code maven-compiler-plugin} argument). On the class path, or without that argument, there is no version: a
 * development run has nothing to update anyway.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public final class AppVersion {

    private AppVersion() {}

    /**
     * @param anchor any class of the application's module
     * @return its module's version, when the module is named and carries one
     */
    public static Optional<Version> of(Class<?> anchor) {
        Objects.requireNonNull(anchor, "anchor");
        Module module = anchor.getModule();
        if (!module.isNamed()) {
            return Optional.empty();
        }
        return Optional.ofNullable(module.getDescriptor()).flatMap(ModuleDescriptor::version);
    }
}
