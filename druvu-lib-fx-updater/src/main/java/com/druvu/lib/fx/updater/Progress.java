package com.druvu.lib.fx.updater;

/**
 * How far a download has come.
 *
 * @param bytesDone the bytes received so far
 * @param bytesTotal the bytes expected, or {@code -1} when the size is unknown
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public record Progress(long bytesDone, long bytesTotal) {

    /** @return the completed share from 0 to 1, or {@code -1} when the total is unknown */
    public double fraction() {
        if (bytesTotal <= 0) {
            return -1;
        }
        return Math.min(1.0, (double) bytesDone / bytesTotal);
    }
}
