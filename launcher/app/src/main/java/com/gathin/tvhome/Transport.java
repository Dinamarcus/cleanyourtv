package com.gathin.tvhome;

import android.media.session.PlaybackState;

/** Which transport buttons a media session offers, from its {@link PlaybackState} actions bitmask. Pure logic. */
final class Transport {
    private Transport() {
    }

    static boolean hasPrevious(long actions) {
        return (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0;
    }

    static boolean hasNext(long actions) {
        return (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0;
    }

    /**
     * Whether the middle button should read Pause: the session is playing or about to (buffering, connecting).
     * Paused, stopped, errored or no state at all reads Play.
     */
    static boolean showsPause(int state) {
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
                || state == PlaybackState.STATE_CONNECTING;
    }

    /**
     * Whether the session lists any way to play or pause. The button stays on screen either way, so there is always
     * something for Up to land on; this only says whether pressing it is expected to do anything.
     */
    static boolean canTogglePlayback(long actions) {
        return (actions & (PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE)) != 0;
    }
}
