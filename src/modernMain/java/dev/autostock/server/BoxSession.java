package dev.autostock.server;

interface BoxSession {
    /** Indices are player inventory 0..35 and box 0..26, independent of screen slot order. */
    void move(int playerSlot, int boxSlot, int count, boolean intoBox);
    void closeVerified();
    String backend();
}
