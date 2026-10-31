package com.druvu.lib.fx.updater.install;

import com.druvu.lib.fx.updater.Installed;
import java.io.IOException;
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.Path;
import java.util.List;

/**
 * Windows: the MSIX package is handed to {@code Add-AppxPackage} with a deferred registration.
 *
 * <p>The package being updated is the one this application runs from, so Windows postpones the switch until the
 * application has exited; the user keeps working. The alternative, {@code -ForceApplicationShutdown}, would kill the
 * application mid-session, before any dialog could announce it.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
final class MsixInstallation implements Installation {

    private static final String SUFFIX = ".msix";
    private static final List<String> POWERSHELL = List.of("powershell.exe", "-NoProfile", "-NonInteractive");

    private final Path installLocation;
    private final long pid;

    MsixInstallation(Path installLocation, long pid) {
        this.installLocation = installLocation;
        this.pid = pid;
    }

    @Override
    public String assetSuffix() {
        return SUFFIX;
    }

    @Override
    public Path location() {
        return installLocation;
    }

    @Override
    public Installed apply(Path download, Version version) throws IOException {
        Shell.run(powerShell(addPackageCommand(download)));
        return new Installed.AtNextStart(version);
    }

    @Override
    public void relaunch() throws IOException {
        Shell.startDetached(powerShell(relaunchCommand(pid, installLocation)));
    }

    /** Adds the package without closing the running application; Windows switches at the next start. */
    static String addPackageCommand(Path msix) {
        return "Add-AppxPackage -Path " + Shell.powerShellQuoted(msix.toString())
                + " -DeferRegistrationWhenPackagesAreInUse";
    }

    /**
     * Finds the package this runtime belongs to by its install folder, waits for the process to end, then starts the
     * package's application through the shell's Apps folder, which resolves to the registered (new) version.
     */
    static String relaunchCommand(long pid, Path installLocation) {
        String location = Shell.powerShellQuoted(installLocation.toString());
        return "$package = Get-AppxPackage | Where-Object { " + location
                + ".StartsWith($_.InstallLocation, [System.StringComparison]::OrdinalIgnoreCase) }"
                + " | Select-Object -First 1; "
                + "$application = (Get-AppxPackageManifest $package).Package.Applications.Application.Id; "
                + "Wait-Process -Id " + pid + " -ErrorAction SilentlyContinue; "
                + "Start-Process ('shell:AppsFolder\\' + $package.PackageFamilyName + '!' + $application)";
    }

    private static List<String> powerShell(String command) {
        List<String> full = new java.util.ArrayList<>(POWERSHELL);
        full.add("-Command");
        full.add(command);
        return List.copyOf(full);
    }

    @Override
    public String toString() {
        return "MSIX package at " + installLocation;
    }
}
