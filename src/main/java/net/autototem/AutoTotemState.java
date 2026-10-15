package net.autototem;

/**
 * Runtime numbers shown on the stats HUD. Nothing here is saved to disk.
 */
public final class AutoTotemState {

    private AutoTotemState() {
    }

    private static volatile int totemsRemaining = 0;
    private static volatile long lastUsedAtMillis = -1L;

    public static int getTotemsRemaining() {
        return totemsRemaining;
    }

    public static void setTotemsRemaining(int value) {
        totemsRemaining = value;
    }

    public static long getLastUsedAtMillis() {
        return lastUsedAtMillis;
    }

    public static void markUsedNow() {
        lastUsedAtMillis = System.currentTimeMillis();
    }

    public static boolean hasBeenUsed() {
        return lastUsedAtMillis > 0;
    }
}
