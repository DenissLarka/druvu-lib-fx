package com.druvu.lib.fx.exec;

/**
 * What a task reports while it runs, handed to it by {@link FxExec#run(String, ProgressRunnable)}. Each report is
 * published as a {@link TaskEvent.Progressed}, so the status bar can show a bar without the task knowing it.
 */
@FunctionalInterface
public interface TaskProgress {

    /**
     * @param fraction how far the task is, from 0 to 1; {@code -1} when the total is unknown, which shows as an
     *     indeterminate bar
     */
    void report(double fraction);
}
