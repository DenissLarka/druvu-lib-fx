package com.druvu.lib.fx.updater;

import java.lang.module.ModuleDescriptor.Version;

/**
 * The application's side of the conversation: everything the user sees and answers. The kit implements it with JavaFX
 * dialogs and the status bar; a console host prints and reads.
 *
 * <p>Threading: every method is called on the thread that runs the {@link Updater}, never the UI thread. A method that
 * asks the user blocks that thread until the answer is in; a method that only shows something may return at once.
 *
 * <p>What is shown when: an automatic check ({@link Updater#checkAtStart()}) is quiet until it has an update to offer,
 * so {@link #upToDate} and a {@link #failed} before consent are only called by {@link Updater#checkNow()}. Once the
 * user said {@link Decision#INSTALL}, every failure is reported, whichever check it was.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public interface UpdateHost {

    /**
     * Offers a newer release.
     *
     * @param update what is on offer
     * @return the user's answer
     */
    Decision offer(Update update);

    /**
     * Reports the download, roughly once per megabyte and once at the end.
     *
     * @param progress how far it is
     */
    void progress(Progress progress);

    /**
     * Tells the user the update is applied and asks about the restart where one would run the new version.
     *
     * @param installed what applying left behind; for {@link Installed.ManualSteps} the host shows the instructions and
     *     the answer is ignored
     * @return the user's answer
     */
    RestartChoice installed(Installed installed);

    /**
     * Tells the user there is nothing newer. Only after a user-triggered check.
     *
     * @param current the running version
     */
    void upToDate(Version current);

    /**
     * Tells the user the check or the update stopped.
     *
     * @param failure where and why, worded for the user
     */
    void failed(UpdateFailure failure);
}
