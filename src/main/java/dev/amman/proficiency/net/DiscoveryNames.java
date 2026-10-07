package dev.amman.proficiency.net;

import net.minecraft.network.chat.Component;

/**
 * Names for places, made the same way on both sides. The server cannot ask whether a client has a
 * translation, so it sends a translatable component with the id-made name as its fallback: the
 * client shows its own translation if there is one and "Skeleton Dungeon" if not.
 */
public final class DiscoveryNames {

    private DiscoveryNames() {
    }

    /** "minecraft:the_end" or "mod:some/warped_tower" becomes "The End" or "Warped Tower". */
    public static String fromId(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        path = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? id : out.toString();
    }

    public static Component component(String key, String id) {
        return Component.translatableWithFallback(key, fromId(id));
    }
}
