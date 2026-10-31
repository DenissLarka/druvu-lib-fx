package com.druvu.lib.fx.exec;

/**
 * Background work that reports how far it is: a download, a long import. Same contract as {@link ThrowingRunnable},
 * with the {@link TaskProgress} to report through.
 */
@FunctionalInterface
public interface ProgressRunnable {

    /**
     * @param progress where to report; a task that never reports simply shows as busy
     * @throws Exception whatever the work throws; it fails the task
     */
    void run(TaskProgress progress) throws Exception;
}
