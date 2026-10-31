package com.druvu.lib.fx.updater;

/**
 * The user's answer to an offered {@link Update}.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public enum Decision {
    /** Download, verify and apply it now. */
    INSTALL,
    /** Not this time; the next check offers it again. */
    NOT_NOW,
    /** Never offer this version again; a later one is offered as usual. */
    SKIP_VERSION
}
