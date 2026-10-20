package com.gathin.tvhome;

import android.media.session.PlaybackState;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for which transport buttons a session's actions allow. */
public class TransportTest {

    @Test
    public void skipButtonsFollowTheirOwnActionBits() {
        assertFalse(Transport.hasPrevious(0));
        assertFalse(Transport.hasNext(0));
        assertTrue(Transport.hasPrevious(PlaybackState.ACTION_SKIP_TO_PREVIOUS));
        assertFalse(Transport.hasNext(PlaybackState.ACTION_SKIP_TO_PREVIOUS));
        assertTrue(Transport.hasNext(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_SKIP_TO_NEXT));
        assertFalse(Transport.hasPrevious(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_SKIP_TO_NEXT));
    }

    @Test
    public void playPauseIsOfferedByAnyOfTheThreeActions() {
        assertFalse(Transport.canTogglePlayback(0));
        assertFalse(Transport.canTogglePlayback(PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SEEK_TO));
        assertTrue(Transport.canTogglePlayback(PlaybackState.ACTION_PLAY));
        assertTrue(Transport.canTogglePlayback(PlaybackState.ACTION_PAUSE));
        assertTrue(Transport.canTogglePlayback(PlaybackState.ACTION_PLAY_PAUSE));
    }

    @Test
    public void theMiddleButtonReadsPauseOnlyWhileAudioIsRunning() {
        assertTrue(Transport.showsPause(PlaybackState.STATE_PLAYING));
        assertTrue(Transport.showsPause(PlaybackState.STATE_BUFFERING));
        assertTrue(Transport.showsPause(PlaybackState.STATE_CONNECTING));
        assertFalse(Transport.showsPause(PlaybackState.STATE_PAUSED));
        assertFalse(Transport.showsPause(PlaybackState.STATE_STOPPED));
        assertFalse(Transport.showsPause(PlaybackState.STATE_NONE));
        assertFalse(Transport.showsPause(PlaybackState.STATE_ERROR));
    }
}
