package com.druvu.lib.fx.updater.fixtures;

import com.druvu.lib.fx.updater.Asset;
import com.druvu.lib.fx.updater.Release;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor.Version;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Builds releases and assets for the tests: a package is a small file served through a {@code file:} URL. */
public final class Assets {

    public static final URI RELEASE_PAGE = URI.create("https://github.com/DenissLarka/example/releases/tag/v1.2.0");

    private Assets() {}

    /** A package file with the given content, in the given folder. */
    public static Path aPackage(Path folder, String name, String content) {
        try {
            return Files.writeString(folder.resolve(name), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The asset for a package file, with its true SHA-256. */
    public static Asset anAssetOf(Path file) {
        return new Asset(file.getFileName().toString(), file.toUri(), size(file), sha256(file));
    }

    /** The asset for a package file, with a SHA-256 that is not the file's. */
    public static Asset aTamperedAssetOf(Path file) {
        return new Asset(file.getFileName().toString(), file.toUri(), size(file), "0".repeat(64));
    }

    /** The asset for a package file whose source publishes no digest. */
    public static Asset anUnverifiableAssetOf(Path file) {
        return new Asset(file.getFileName().toString(), file.toUri(), size(file), "");
    }

    public static Release aRelease(String version, Asset... assets) {
        return new Release(Version.parse(version), RELEASE_PAGE, "Notes of " + version, List.of(assets));
    }

    public static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
