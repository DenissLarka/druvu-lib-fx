package com.druvu.lib.fx.updater.install;

import com.druvu.lib.fx.updater.Installed;
import java.io.IOException;
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

/**
 * Linux: the running {@code .AppImage} file is replaced in place.
 *
 * <p>An AppImage runs from a temporary mount of its content, so the file itself is not busy while the application runs.
 * The new file is copied next to it, made executable and moved over it in one atomic step; the running process stays
 * the old version until it exits.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
final class AppImageInstallation implements Installation {

    private static final String SUFFIX = ".AppImage";
    private static final String STAGING_SUFFIX = ".new";

    private final Path running;
    private final long pid;

    AppImageInstallation(Path running, long pid) {
        this.running = running;
        this.pid = pid;
    }

    @Override
    public String assetSuffix() {
        return SUFFIX;
    }

    @Override
    public Path location() {
        return running;
    }

    @Override
    public Installed apply(Path download, Version version) throws IOException {
        Path staging = running.resolveSibling(running.getFileName() + STAGING_SUFFIX);
        Files.copy(download, staging, StandardCopyOption.REPLACE_EXISTING);
        makeExecutable(staging);
        Files.move(staging, running, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return new Installed.Replaced(version);
    }

    @Override
    public void relaunch() throws IOException {
        Shell.startDetached(List.of("sh", "-c", relaunchScript(pid, running)));
    }

    /** Waits for the process to end, then runs the file at the same path, which is the new version by then. */
    static String relaunchScript(long pid, Path running) {
        return "while kill -0 " + pid + " 2>/dev/null; do sleep 0.2; done; exec "
                + Shell.singleQuoted(running.toString());
    }

    private static void makeExecutable(Path file) throws IOException {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
        permissions.add(PosixFilePermission.OWNER_EXECUTE);
        permissions.add(PosixFilePermission.GROUP_EXECUTE);
        permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        Files.setPosixFilePermissions(file, permissions);
    }

    @Override
    public String toString() {
        return "AppImage " + running;
    }
}
