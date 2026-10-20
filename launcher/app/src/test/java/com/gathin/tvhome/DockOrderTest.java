package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the dock list operations behind the tile menu. */
public class DockOrderTest {

    private static List<String> list(String... pkgs) {
        return new ArrayList<>(Arrays.asList(pkgs));
    }

    // ---- move ------------------------------------------------------------------------------------

    @Test
    public void moveLeftSwapsWithTheNeighbour() {
        assertEquals(list("b", "a", "c", "d"), DockOrder.moveLeft(list("a", "b", "c", "d"), "b"));
        assertEquals(list("a", "c", "b", "d"), DockOrder.moveLeft(list("a", "b", "c", "d"), "c"));
    }

    @Test
    public void moveRightSwapsWithTheNeighbour() {
        assertEquals(list("a", "c", "b", "d"), DockOrder.moveRight(list("a", "b", "c", "d"), "b"));
        assertEquals(list("a", "b", "d", "c"), DockOrder.moveRight(list("a", "b", "c", "d"), "c"));
    }

    @Test
    public void movingPastAnEndOrAnUnknownAppChangesNothing() {
        assertEquals(list("a", "b", "c"), DockOrder.moveLeft(list("a", "b", "c"), "a"));
        assertEquals(list("a", "b", "c"), DockOrder.moveRight(list("a", "b", "c"), "c"));
        assertEquals(list("a", "b", "c"), DockOrder.moveLeft(list("a", "b", "c"), "zzz"));
        assertEquals(list("a", "b", "c"), DockOrder.moveRight(list("a", "b", "c"), "zzz"));
    }

    @Test
    public void canMoveHidesTheDirectionsThatGoNowhere() {
        List<String> dock = list("a", "b", "c");
        assertFalse(DockOrder.canMoveLeft(dock, "a"));
        assertTrue(DockOrder.canMoveRight(dock, "a"));
        assertTrue(DockOrder.canMoveLeft(dock, "b"));
        assertTrue(DockOrder.canMoveRight(dock, "b"));
        assertTrue(DockOrder.canMoveLeft(dock, "c"));
        assertFalse(DockOrder.canMoveRight(dock, "c"));
        assertFalse(DockOrder.canMoveLeft(dock, "zzz"));
        assertFalse(DockOrder.canMoveRight(dock, "zzz"));
    }

    @Test
    public void aLoneAppCannotMove() {
        assertFalse(DockOrder.canMoveLeft(list("a"), "a"));
        assertFalse(DockOrder.canMoveRight(list("a"), "a"));
    }

    // ---- add -------------------------------------------------------------------------------------

    @Test
    public void addAppendsWhileThereIsRoom() {
        assertEquals(list("a", "b", "x"), DockOrder.add(list("a", "b"), "x", 4));
        assertEquals(list("x"), DockOrder.add(Collections.<String>emptyList(), "x", 4));
    }

    @Test
    public void addToAFullDockDropsTheLastApp() {
        assertEquals(list("a", "b", "c", "x"), DockOrder.add(list("a", "b", "c", "d"), "x", 4));
    }

    @Test
    public void addToADockOfOneColumnReplacesIt() {
        assertEquals(list("x"), DockOrder.add(list("a"), "x", 1));
    }

    @Test
    public void addToADockLongerThanTheColumnsTrimsItToFit() {
        // A hand-edited dock_apps longer than the row: the result still fits the row.
        assertEquals(list("a", "b", "c", "x"), DockOrder.add(list("a", "b", "c", "d", "e"), "x", 4));
    }

    @Test
    public void addingAnAppAlreadyThereChangesNothing() {
        assertEquals(list("a", "b"), DockOrder.add(list("a", "b"), "a", 4));
    }

    // ---- remove ----------------------------------------------------------------------------------

    @Test
    public void removeTakesTheAppOutAndKeepsTheOrder() {
        assertEquals(list("a", "c", "d"), DockOrder.remove(list("a", "b", "c", "d"), "b"));
        assertEquals(list("a", "b"), DockOrder.remove(list("a", "b"), "zzz"));
    }

    @Test
    public void operationsLeaveTheirArgumentAlone() {
        List<String> dock = list("a", "b", "c");
        DockOrder.moveLeft(dock, "b");
        DockOrder.moveRight(dock, "b");
        DockOrder.add(dock, "x", 3);
        DockOrder.remove(dock, "b");
        assertEquals(list("a", "b", "c"), dock);
    }

    // ---- what gets written -----------------------------------------------------------------------

    @Test
    public void persistedKeepsFavouritesThatWereNotOnScreen() {
        // "ghost" is pinned but not installed, "e" is a fifth favourite below the dock.
        List<String> oldFavs = list("a", "ghost", "b", "c", "d", "e");
        List<String> oldDock = list("a", "b", "c", "d");
        assertEquals(list("b", "a", "c", "d", "ghost", "e"),
                DockOrder.persisted(DockOrder.moveLeft(oldDock, "b"), oldDock, oldFavs));
    }

    @Test
    public void persistedDoesNotBringBackAnAppTakenOutOnPurpose() {
        List<String> oldFavs = list("a", "b", "c", "d");
        List<String> oldDock = list("a", "b", "c", "d");
        // Removed: gone. Dropped by an add to a full dock: gone from the dock too.
        assertEquals(list("a", "c", "d"), DockOrder.persisted(DockOrder.remove(oldDock, "b"), oldDock, oldFavs));
        assertEquals(list("a", "b", "c", "x"), DockOrder.persisted(DockOrder.add(oldDock, "x", 4), oldDock, oldFavs));
    }

    @Test
    public void persistedWritesTheFillersTheDockShowed() {
        // Two favourites installed, the other two slots showed the first apps of the grid: they are pinned now.
        List<String> oldFavs = list("a", "b");
        List<String> oldDock = list("a", "b", "g1", "g2");
        assertEquals(list("a", "b", "g1", "g2"), DockOrder.persisted(list("a", "b", "g1", "g2"), oldDock, oldFavs));
        assertEquals(list("b", "a", "g1", "g2"), DockOrder.persisted(DockOrder.moveRight(oldDock, "a"), oldDock, oldFavs));
    }

    @Test
    public void persistedNeverRepeatsAnApp() {
        List<String> out = DockOrder.persisted(list("a", "b"), list("a", "b"), list("a", "a", "b", "c", "c"));
        assertEquals(list("a", "b", "c"), out);
    }
}
