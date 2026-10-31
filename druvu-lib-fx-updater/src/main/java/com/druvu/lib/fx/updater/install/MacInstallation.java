package com.druvu.lib.fx.updater.install;

import com.druvu.lib.fx.updater.Installed;
import java.io.IOException;
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.Path;
import java.util.List;

/**
 * macOS: the disk image is mounted and opened in Finder; the user drags the new bundle over the installed one.
 *
 * <p>A running bundle cannot be replaced from inside itself, and replacing it needs no privilege the user lacks, so for
 * now the user does the one step by hand. A helper process that swaps the bundle after the exit is the later
 * improvement; until then there is no restart to offer.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
final class MacInstallation implements Installation {

    private static final String SUFFIX = ".dmg";

    private final Path bundle;
    private final long pid;

    MacInstallation(Path bundle, long pid) {
        this.bundle = bundle;
        this.pid = pid;
    }

    @Override
    public String assetSuffix() {
        return SUFFIX;
    }

    @Override
    public Path location() {
        return bundle;
    }

    @Override
    public Installed apply(Path download, Version version) throws IOException {
        Shell.run(List.of("hdiutil", "attach", "-nobrowse", download.toString()));
        // Opening the image file once it is attached brings its volume up in Finder.
        Shell.run(List.of("open", download.toString()));
        return new Installed.ManualSteps(version, instructions(version, bundle));
    }

    @Override
    public void relaunch() throws IOException {
        Shell.startDetached(List.of("sh", "-c", relaunchScript(pid, bundle)));
    }

    static String instructions(Version version, Path bundle) {
        Path fileName = bundle.getFileName();
        String name = fileName == null ? bundle.toString() : fileName.toString();
        return "Version " + version + " is open in Finder.\n"
                + "1. Drag " + name + " to the Applications folder and replace the one there.\n"
                + "2. Quit and start it again.";
    }

    /** Waits for the process to end, then opens the bundle again, as a new instance. */
    static String relaunchScript(long pid, Path bundle) {
        return "while kill -0 " + pid + " 2>/dev/null; do sleep 0.2; done; open -n "
                + Shell.singleQuoted(bundle.toString());
    }

    @Override
    public String toString() {
        return "macOS bundle " + bundle;
    }
}
