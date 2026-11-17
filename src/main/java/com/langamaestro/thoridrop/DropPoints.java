package com.langamaestro.thoridrop;

import java.nio.file.*;
import java.util.*;

/** Server-authoritative, durable points ledger for Thoridrop rewards. */
public final class DropPoints {
    private static final Path FILE = Paths.get("config", "thoridrop-points.properties");
    private static final Properties DATA = new Properties();

    private DropPoints() {}

    public static synchronized void load() {
        DATA.clear();
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.exists(FILE)) try (var in = Files.newInputStream(FILE)) { DATA.load(in); }
        } catch (Exception ex) { throw new IllegalStateException("Cannot load Thoridrop points", ex); }
    }

    public static synchronized int balance(UUID player) {
        try { return Math.max(0, Integer.parseInt(DATA.getProperty(player.toString(), "0"))); }
        catch (NumberFormatException ex) { return 0; }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (var out = Files.newOutputStream(tmp)) { DATA.store(out, "Thoridrop player balances"); }
            try { Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception ex) { throw new IllegalStateException("Cannot save Thoridrop points", ex); }
    }

    public static synchronized int earn(UUID player, int points) {
        if (points < 0) throw new IllegalArgumentException("Negative points");
        int next = Math.toIntExact((long) balance(player) + points);
        DATA.setProperty(player.toString(), Integer.toString(next));
        save();
        return next;
    }

    public static synchronized boolean spend(UUID player, int cost) {
        if (cost < 1) throw new IllegalArgumentException("Invalid shop price");
        int current = balance(player);
        if (current < cost) return false;
        DATA.setProperty(player.toString(), Integer.toString(current - cost));
        save();
        return true;
    }
}
