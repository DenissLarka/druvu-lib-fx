package com.druvu.lib.fx.update;

import com.druvu.lib.fx.exec.TaskProgress;
import com.druvu.lib.fx.updater.Decision;
import com.druvu.lib.fx.updater.Installed;
import com.druvu.lib.fx.updater.Progress;
import com.druvu.lib.fx.updater.RestartChoice;
import com.druvu.lib.fx.updater.Update;
import com.druvu.lib.fx.updater.UpdateFailure;
import com.druvu.lib.fx.updater.UpdateHost;
import com.druvu.lib.fx.util.FxThreads;
import java.lang.module.ModuleDescriptor.Version;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javafx.stage.Window;

/**
 * The JavaFX side of the conversation: every question becomes a dialog on the owner window, shown on the JavaFX thread
 * while the updater's thread waits for the answer; the download's progress goes to the task the check runs as, so the
 * status bar shows it.
 */
final class FxUpdateHost implements UpdateHost {

    private final Window owner;
    private final String appName;
    private final PrefsUpdateSettings settings;
    private volatile TaskProgress progress = fraction -> {};

    FxUpdateHost(Window owner, String appName, PrefsUpdateSettings settings) {
        this.owner = owner;
        this.appName = appName;
        this.settings = settings;
    }

    /** The task the current check runs as; its reports show in the status bar. */
    void reportTo(TaskProgress progress) {
        this.progress = progress;
    }

    @Override
    public Decision offer(Update update) {
        return onFxAndWait(() -> UpdateDialogs.offer(owner, appName, update, settings.checkAtStartProperty()));
    }

    @Override
    public void progress(Progress downloaded) {
        progress.report(downloaded.fraction());
    }

    @Override
    public RestartChoice installed(Installed installed) {
        return onFxAndWait(() -> UpdateDialogs.installed(owner, appName, installed));
    }

    @Override
    public void upToDate(Version current) {
        onFxAndWait(() -> {
            UpdateDialogs.upToDate(owner, appName, current);
            return null;
        });
    }

    @Override
    public void failed(UpdateFailure failure) {
        onFxAndWait(() -> {
            UpdateDialogs.failed(owner, appName, failure);
            return null;
        });
    }

    /** Runs a dialog on the JavaFX thread and waits for its answer; never called from the JavaFX thread itself. */
    private static <T> T onFxAndWait(Supplier<T> dialog) {
        FxThreads.requireOffFx();
        return CompletableFuture.supplyAsync(dialog, FxThreads.fxExecutor()).join();
    }
}
