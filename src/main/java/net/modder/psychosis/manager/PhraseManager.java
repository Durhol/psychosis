package net.modder.psychosis.manager;

import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Keeps category language separate from scanning and rendering. */
public final class PhraseManager {
    private static final EnumMap<MarkerCategory, List<String>> PHRASES = new EnumMap<>(MarkerCategory.class);

    static {
        PHRASES.put(MarkerCategory.PLAYER, List.of(
                "friend ???", "is it human?", "he sees me", "don't come close", "fake?", "it's looking", "who is that?"));
        PHRASES.put(MarkerCategory.MOB, List.of(
                "is it human?", "it moved", "don't stare", "hungry?", "not animal", "it knows", "watching us"));
        PHRASES.put(MarkerCategory.DOOR, List.of(
                "Protect me, pls!", "lock it", "open...?", "they inside", "don't leave", "close it now"));
        PHRASES.put(MarkerCategory.WINDOW, List.of(
                "window...????", "who is there?", "eyes outside", "don't look", "watching us"));
        PHRASES.put(MarkerCategory.STRUCTURE, List.of(
                "Is it safe there?", "empty inside", "home?", "ruins...", "silent", "nobody came back"));
        PHRASES.put(MarkerCategory.SKYLINE, List.of(
                "Is this an angel?", "fall down", "so high", "above us", "don't jump"));
    }

    private PhraseManager() { }

    public static String next(MarkerCategory category, Random random, Set<String> alreadyVisible) {
        List<String> pool = PHRASES.get(category);
        int start = random.nextInt(pool.size());
        for (int offset = 0; offset < pool.size(); offset++) {
            String candidate = pool.get((start + offset) % pool.size());
            if (!alreadyVisible.contains(candidate)) return candidate;
        }
        return pool.get(start);
    }
}
