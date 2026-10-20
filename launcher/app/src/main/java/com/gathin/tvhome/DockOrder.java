package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * The list operations behind the tile menu (hold OK on a tile): move an app along the dock, add one
 * from the grid, take one out. Pure functions over package names, so they run in plain JVM tests;
 * every one returns a new list and leaves its argument alone.
 */
final class DockOrder {
    private DockOrder() {
    }

    /** Whether the app has a dock neighbour on its left. */
    static boolean canMoveLeft(List<String> dock, String pkg) {
        return dock.indexOf(pkg) > 0;
    }

    /** Whether the app has a dock neighbour on its right (false on the last dock slot). */
    static boolean canMoveRight(List<String> dock, String pkg) {
        int i = dock.indexOf(pkg);
        return i >= 0 && i < dock.size() - 1;
    }

    static List<String> moveLeft(List<String> dock, String pkg) {
        return swap(dock, dock.indexOf(pkg), dock.indexOf(pkg) - 1);
    }

    static List<String> moveRight(List<String> dock, String pkg) {
        return swap(dock, dock.indexOf(pkg), dock.indexOf(pkg) + 1);
    }

    /**
     * Appends the app as the last dock slot. A dock that already has {@code columns} apps loses its
     * last one first, so that app goes back to the grid.
     */
    static List<String> add(List<String> dock, String pkg, int columns) {
        List<String> out = new ArrayList<>(dock);
        if (out.contains(pkg)) return out;
        while (!out.isEmpty() && out.size() >= Math.max(1, columns)) out.remove(out.size() - 1);
        out.add(pkg);
        return out;
    }

    static List<String> remove(List<String> dock, String pkg) {
        List<String> out = new ArrayList<>(dock);
        out.remove(pkg);
        return out;
    }

    /**
     * What to write as dock_apps. The edited dock comes first; favourites that were not on screen
     * (not installed, or past the last dock slot) keep their place behind it, so nothing the owner
     * once pinned is forgotten. An app that was in the old dock and is not in the new one was taken
     * out on purpose and does not come back.
     */
    static List<String> persisted(List<String> newDock, List<String> oldDock, List<String> oldFavourites) {
        List<String> out = new ArrayList<>(newDock);
        for (String f : oldFavourites) {
            if (!out.contains(f) && !oldDock.contains(f)) out.add(f);
        }
        return out;
    }

    private static List<String> swap(List<String> dock, int from, int to) {
        List<String> out = new ArrayList<>(dock);
        if (from < 0 || to < 0 || from >= out.size() || to >= out.size()) return out;
        String moved = out.get(from);
        out.set(from, out.get(to));
        out.set(to, moved);
        return out;
    }
}
