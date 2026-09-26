package com.druvu.lib.fx.util;

import java.util.Objects;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * Runs an action once, a short while after the last of a burst of triggers - what a save-on-change needs, so a window
 * drag or a divider drag costs one write instead of hundreds. FX thread only, like the {@link PauseTransition} under
 * it.
 *
 * @author Deniss Larka <br>
 *     on 26 Sep 2026
 */
public final class Debounce {

    private final PauseTransition pending;

    public Debounce(Duration delay, Runnable action) {
        Objects.requireNonNull(action, "action");
        pending = new PauseTransition(Objects.requireNonNull(delay, "delay"));
        pending.setOnFinished(event -> action.run());
    }

    /** Starts, or restarts, the wait: the action runs once the delay passes with no further trigger. */
    public void trigger() {
        pending.playFromStart();
    }

    /** Drops a pending run, for a caller that is about to do the action itself. */
    public void cancel() {
        pending.stop();
    }
}
