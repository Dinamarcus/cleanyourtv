package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;

import java.io.File;

/** Decodes a theme's wallpaper for the home screen and the screensaver. */
final class Wallpaper {
    private Wallpaper() {
    }

    /**
     * Decodes the picture downsampled to the screen, so a 4K JPEG costs a 1080p bitmap at most (about
     * 4 MB as RGB_565), which is the price of a wallpaper on a 1 GB TV. Null when it cannot be decoded
     * (bad file, or not enough memory): a bad picture must never take the home screen down.
     */
    static Bitmap decode(File file, DisplayMetrics dm) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), opts);
            int sample = 1;
            while (opts.outWidth / (sample * 2) >= dm.widthPixels && opts.outHeight / (sample * 2) >= dm.heightPixels) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;   // no alpha needed, half the memory
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
