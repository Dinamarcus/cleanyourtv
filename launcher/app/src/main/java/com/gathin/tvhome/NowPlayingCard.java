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
import android.view.View;
import android.view.ViewOutlineProvider;
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
 * Reading media sessions needs notification access for {@link MediaListenerService}; without it Android
 * throws a SecurityException, which is caught here and leaves the card hidden. Everything is released in
 * {@link #stop()}, so nothing listens while Home is not in front.
 */
final class NowPlayingCard {
    private static final long HIDE_DELAY_MS = 4000L;
    private static final int DEFAULT_BORDER = 0x40FFFFFF;

    private final Context ctx;
    private final Handler handler;
    private final View card;
    private final ImageView art;
    private final TextView title;
    private final TextView artist;
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
    private boolean hidePending;
    private String boundKey = "";
    private boolean boundArt;

    /** Runs a few seconds after the last playing session paused. */
    private final Runnable hideTask = () -> {
        hidePending = false;
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

    NowPlayingCard(Context ctx, Handler handler, View card, boolean reducedMotion) {
        this.ctx = ctx;
        this.handler = handler;
        this.card = card;
        art = card.findViewById(R.id.nowPlayingArt);
        title = card.findViewById(R.id.nowPlayingTitle);
        artist = card.findViewById(R.id.nowPlayingArtist);
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
        shown = false;
        boundKey = "";
        boundArt = false;
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
        for (MediaController c : controllers) {
            try {
                PlaybackState state = c.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    meta = c.getMetadata();
                    break;
                }
            } catch (RuntimeException ignored) {
            }
        }
        String name = meta != null ? text(meta, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE) : "";
        if (name.isEmpty()) {
            // Paused, stopped, or playing with nothing to say (a video app that sends no metadata).
            if (wanted && !hidePending) {
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

    private void bind(MediaMetadata meta, String name) {
        String by = text(meta, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        Bitmap cover = firstBitmap(meta);
        String key = name + '\0' + by;
        // The same track again (a state update): only a cover that arrived late is new.
        if (key.equals(boundKey) && (boundArt || cover == null)) return;
        boundKey = key;
        title.setText(name);
        artist.setText(by);
        artist.setVisibility(by.isEmpty() ? View.GONE : View.VISIBLE);
        int accent = ArtColor.NONE;
        Bitmap small = cover != null ? scaleToCover(cover) : null;
        if (small != null) {
            art.setImageBitmap(small);
            accent = accentOf(small);
        } else {
            art.setImageDrawable(null);
        }
        boundArt = small != null;
        if (background != null) {
            background.setStroke(borderPx, accent != ArtColor.NONE ? (accent & 0x00FFFFFF) | 0xB3000000 : DEFAULT_BORDER);
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
