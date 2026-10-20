package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The small glass card above the dock that names what is playing. While Home is in front it follows the
 * active media sessions and shows the first one that is playing: title, artist and the cover, with a thin
 * border in the cover's colour. It fades in on play and out a few seconds after pause or stop.
 *
 * Under the text sit three round buttons: previous, play/pause and next, sent to the session that is playing (or
 * played last). They are the only focusable part of the card; their D-pad rules live here, and the card asks its
 * {@link Host} to move focus out of it. While one has focus the card stays up even if playback pauses.
 *
 * Reading media sessions needs notification access for {@link MediaListenerService}; without it Android
 * throws a SecurityException, which is caught here and leaves the card hidden. Everything is released in
 * {@link #stop()}, so nothing listens while Home is not in front.
 */
final class NowPlayingCard {
    private static final long HIDE_DELAY_MS = 4000L;
    private static final int DEFAULT_BORDER = 0x40FFFFFF;

    /** Where focus goes when it leaves the card. */
    interface Host {
        /** Up from the card: the top bar. */
        void focusTopBar();

        /** Down from the card, or the session vanished under focus: the dock tile that had focus last. */
        void focusDock();
    }

    private final Context ctx;
    private final Handler handler;
    private final View card;
    private final ImageView art;
    private final TextView title;
    private final TextView artist;
    private final ImageButton prev;
    private final ImageButton playPause;
    private final ImageButton next;
    private final View[] buttons;
    private final Host host;
    private final GradientDrawable background;
    private final int artPx;
    private final int borderPx;
    private final long fadeMs;

    private MediaSessionManager manager;
    private boolean listening;
    private List<MediaController> controllers = Collections.emptyList();
    /** Something is playing and has a title: the card wants to be on screen. */
    private boolean wanted;
    /** Focus is in the grid below the dock, where the card would cover tiles. */
    private boolean obscured;
    private boolean shown;
    /** The first session that is playing now, else the one that played last while it is still active. */
    private MediaController active;
    /** The active session is playing and has a title; false while paused, stopped or gone. */
    private boolean playing;
    /** Focus is on one of the three buttons. */
    private boolean focusInside;
    private boolean pauseIcon;
    private boolean hidePending;
    private String boundKey = "";
    /** Signature of the cover on screen (see signature); 0 when none. */
    private long boundCoverSig;

    /** Runs a few seconds after the last playing session paused. */
    private final Runnable hideTask = () -> {
        hidePending = false;
        if (focusInside) return;   // whoever is on a button is not done; setFocusInside(false) rearms this
        wanted = false;
        render();
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::adopt;

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            evaluate();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            evaluate();
        }
    };

    NowPlayingCard(Context ctx, Handler handler, View card, boolean reducedMotion, Host host) {
        this.ctx = ctx;
        this.handler = handler;
        this.card = card;
        this.host = host;
        art = card.findViewById(R.id.nowPlayingArt);
        title = card.findViewById(R.id.nowPlayingTitle);
        artist = card.findViewById(R.id.nowPlayingArtist);
        prev = card.findViewById(R.id.nowPlayingPrev);
        playPause = card.findViewById(R.id.nowPlayingPlayPause);
        next = card.findViewById(R.id.nowPlayingNext);
        buttons = new View[]{prev, playPause, next};
        prev.setOnClickListener(v -> send(Control.PREVIOUS));
        playPause.setOnClickListener(v -> send(Control.TOGGLE));
        next.setOnClickListener(v -> send(Control.NEXT));
        for (View b : buttons) b.setOnKeyListener(this::onButtonKey);
        float density = ctx.getResources().getDisplayMetrics().density;
        artPx = (int) (64 * density);
        borderPx = Math.max(1, (int) (1.5f * density));
        fadeMs = reducedMotion ? 120 : 350;
        background = card.getBackground() instanceof GradientDrawable
                ? (GradientDrawable) card.getBackground().mutate() : null;
        // Round the cover with the GPU clip instead of masking the bitmap.
        final float radius = 10 * density;
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        art.setClipToOutline(true);
    }

    /** Starts following the media sessions (Home resumed). Quietly does nothing without notification access. */
    void start() {
        if (listening) return;
        try {
            manager = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return;
            ComponentName component = new ComponentName(ctx, MediaListenerService.class);
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler);
            listening = true;
            adopt(manager.getActiveSessions(component));
        } catch (RuntimeException e) {
            // SecurityException: notification access not granted. Anything else: no card either.
            stop();
        }
    }

    /** Releases every callback and hides the card at once (Home paused). */
    void stop() {
        if (focusInside) {
            host.focusDock();   // before the buttons go away, so focus lands somewhere known
            focusInside = false;
        }
        handler.removeCallbacks(hideTask);
        hidePending = false;
        if (listening && manager != null) {
            try {
                manager.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (RuntimeException ignored) {
            }
        }
        listening = false;
        release(controllers);
        controllers = Collections.emptyList();
        wanted = false;
        playing = false;
        active = null;
        shown = false;
        boundKey = "";
        boundCoverSig = 0L;
        card.animate().cancel();
        card.setAlpha(0f);
        card.setVisibility(View.GONE);
    }

    /** Hides the card while focus is below the dock, where scrolled tiles would run under it. */
    void setObscured(boolean value) {
        if (obscured == value) return;
        obscured = value;
        render();
    }

    /** True when {@code v} is one of the card's buttons. */
    boolean owns(View v) {
        return v == prev || v == playPause || v == next;
    }

    /**
     * Up from a dock tile: moves focus to Play/Pause when the card is on screen. Returns false when the card is
     * hidden or faded out, so the key keeps its usual meaning.
     */
    boolean focusEntry() {
        return shown && playPause.requestFocus();
    }

    /**
     * Tells the card whether focus is on one of its buttons (from the activity's global focus listener). Leaving
     * while nothing plays starts the usual fade-out delay; entering cancels a pending one.
     */
    void setFocusInside(boolean value) {
        if (focusInside == value) return;
        focusInside = value;
        if (value) {
            handler.removeCallbacks(hideTask);
            hidePending = false;
        } else if (wanted && !playing && !hidePending) {
            hidePending = true;
            handler.postDelayed(hideTask, HIDE_DELAY_MS);
        }
    }

    /**
     * Puts the card's bottom edge {@code gap} px above the dock band. The card sits in the bottom right
     * corner of the root and is moved up with translation, so no layout pass is needed.
     */
    void placeAbove(int rootHeight, int bandTop, int gap) {
        card.setTranslationY(-(rootHeight - bandTop + gap));
    }

    private void adopt(List<MediaController> next) {
        release(controllers);
        controllers = next != null ? new ArrayList<>(next) : Collections.<MediaController>emptyList();
        for (MediaController c : controllers) {
            try {
                c.registerCallback(controllerCallback, handler);
            } catch (RuntimeException ignored) {
            }
        }
        evaluate();
    }

    private void release(List<MediaController> list) {
        for (MediaController c : list) {
            try {
                c.unregisterCallback(controllerCallback);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Looks for the first playing session; shows its track, or lets the card fade out after a pause. */
    private void evaluate() {
        MediaMetadata meta = null;
        MediaController found = null;
        for (MediaController c : controllers) {
            try {
                PlaybackState state = c.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    meta = c.getMetadata();
                    found = c;
                    break;
                }
            } catch (RuntimeException ignored) {
            }
        }
        // Keep steering the session that played last while it is paused; sessions are new objects on every
        // list change, so match by token.
        active = found != null ? found : sameSession(active);
        String name = meta != null ? text(meta, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE) : "";
        playing = !name.isEmpty();
        updateButtons();
        if (active == null && focusInside) host.focusDock();   // nothing left to control: leave before the card fades
        if (name.isEmpty()) {
            // Paused, stopped, or playing with nothing to say (a video app that sends no metadata).
            if (wanted && !hidePending && !focusInside) {
                hidePending = true;
                handler.postDelayed(hideTask, HIDE_DELAY_MS);
            }
            return;
        }
        handler.removeCallbacks(hideTask);
        hidePending = false;
        bind(meta, name);
        wanted = true;
        render();
    }

    /** The controller in the current list that is the same session as {@code old}, or null. */
    private MediaController sameSession(MediaController old) {
        if (old == null) return null;
        try {
            for (MediaController c : controllers) {
                if (c.getSessionToken().equals(old.getSessionToken())) return c;
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /** Shows what the active session offers: Previous and Next only when listed, Play or Pause by state. */
    private void updateButtons() {
        long actions = 0;
        int state = PlaybackState.STATE_NONE;
        if (active != null) {
            try {
                PlaybackState ps = active.getPlaybackState();
                if (ps != null) {
                    actions = ps.getActions();
                    state = ps.getState();
                }
            } catch (RuntimeException ignored) {
            }
        }
        // A focused button is about to disappear: hand focus to the one that always stays before hiding it.
        setSkipVisible(prev, Transport.hasPrevious(actions));
        setSkipVisible(next, Transport.hasNext(actions));
        boolean showPause = Transport.showsPause(state);
        if (showPause != pauseIcon) {
            pauseIcon = showPause;
            playPause.setImageResource(showPause ? R.drawable.ic_media_pause : R.drawable.ic_media_play);
            playPause.setContentDescription(ctx.getString(showPause ? R.string.media_pause : R.string.media_play));
        }
        playPause.setAlpha(Transport.canTogglePlayback(actions) ? 1f : 0.4f);
    }

    private void setSkipVisible(View button, boolean visible) {
        if (!visible && button.hasFocus()) playPause.requestFocus();
        button.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private enum Control { PREVIOUS, TOGGLE, NEXT }

    /** Sends one button press to the active session. A session that died meanwhile is ignored. */
    private void send(Control control) {
        MediaController c = active;
        if (c == null) return;
        try {
            MediaController.TransportControls t = c.getTransportControls();
            if (control == Control.PREVIOUS) {
                t.skipToPrevious();
            } else if (control == Control.NEXT) {
                t.skipToNext();
            } else {
                PlaybackState ps = c.getPlaybackState();
                if (ps != null && !Transport.canTogglePlayback(ps.getActions())) return;
                if (pauseIcon) t.pause();
                else t.play();
            }
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * D-pad on a button. Left and Right step over the visible buttons and stop at the ends, Up goes to the top
     * bar and Down back to the dock, so the focus finder never picks a neighbour of its own.
     */
    private boolean onButtonKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            host.focusTopBar();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            host.focusDock();
            return true;
        }
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        int step = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
        int i = 0;
        while (i < buttons.length && buttons[i] != v) i++;
        for (i += step; i >= 0 && i < buttons.length; i += step) {
            if (buttons[i].getVisibility() == View.VISIBLE) {
                buttons[i].requestFocus();
                break;
            }
        }
        return true;   // at an end: stay put
    }

    private void bind(MediaMetadata meta, String name) {
        String by = text(meta, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        Bitmap cover = firstBitmap(meta);
        String key = name + '\0' + by;
        long coverSig = signature(cover);
        // Players often send the new title with the previous cover and the right cover a moment later, so the
        // cover is compared by its own signature, not only by whether the track changed.
        boolean sameTrack = key.equals(boundKey);
        if (sameTrack && (coverSig == boundCoverSig || cover == null)) return;
        boundKey = key;
        boundCoverSig = coverSig;
        if (!sameTrack) {
            title.setText(name);
            artist.setText(by);
            artist.setVisibility(by.isEmpty() ? View.GONE : View.VISIBLE);
        }
        int accent = ArtColor.NONE;
        Bitmap small = cover != null ? scaleToCover(cover) : null;
        if (small != null) {
            art.setImageBitmap(small);
            accent = accentOf(small);
        } else {
            art.setImageDrawable(null);
        }
        if (background != null) {
            background.setStroke(borderPx, accent != ArtColor.NONE ? (accent & 0x00FFFFFF) | 0xB3000000 : DEFAULT_BORDER);
        }
    }

    /** Cheap identity for a cover: its size plus a 4 by 4 grid of sampled pixels. 0 for none. */
    private static long signature(Bitmap b) {
        if (b == null) return 0L;
        try {
            int w = b.getWidth(), h = b.getHeight();
            long sig = 31L * w + h;
            for (int y = 1; y <= 4; y++) {
                for (int x = 1; x <= 4; x++) {
                    sig = sig * 1_000_003L + b.getPixel(Math.min(w - 1, w * x / 5), Math.min(h - 1, h * y / 5));
                }
            }
            return sig == 0L ? 1L : sig;
        } catch (RuntimeException e) {
            return 1L;
        }
    }

    /** The cover scaled so its short side is the art size; the ImageView crops the rest. Null if it cannot be done. */
    private Bitmap scaleToCover(Bitmap src) {
        try {
            int w = src.getWidth(), h = src.getHeight();
            if (w <= 0 || h <= 0) return null;
            float k = artPx / (float) Math.min(w, h);
            return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)), true);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /** The accent from an 8 by 8 copy: 64 pixels are plenty for an average. */
    private static int accentOf(Bitmap small) {
        try {
            Bitmap tiny = Bitmap.createScaledBitmap(small, 8, 8, true);
            int[] pixels = new int[64];
            tiny.getPixels(pixels, 0, 8, 0, 0, 8, 8);
            if (tiny != small) tiny.recycle();
            return ArtColor.accent(pixels);
        } catch (RuntimeException | OutOfMemoryError e) {
            return ArtColor.NONE;
        }
    }

    private static Bitmap firstBitmap(MediaMetadata meta) {
        String[] keys = {MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON};
        for (String k : keys) {
            try {
                Bitmap b = meta.getBitmap(k);
                if (b != null && !b.isRecycled()) return b;
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    private static String text(MediaMetadata meta, String... keys) {
        for (String k : keys) {
            CharSequence t = meta.getText(k);
            if (t != null && t.toString().trim().length() > 0) return t.toString().trim();
        }
        return "";
    }

    /** Fades the card to what it should be now: on screen when something plays and focus is on the dock. */
    private void render() {
        boolean visible = wanted && !obscured;
        if (visible == shown) return;
        shown = visible;
        card.animate().cancel();
        if (visible) {
            card.setVisibility(View.VISIBLE);
            card.animate().alpha(1f).setDuration(fadeMs).start();
        } else {
            card.animate().alpha(0f).setDuration(fadeMs).withEndAction(() -> {
                if (!shown) card.setVisibility(View.GONE);
            }).start();
        }
    }
}
