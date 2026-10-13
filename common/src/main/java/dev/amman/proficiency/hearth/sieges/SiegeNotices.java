package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.SiegeRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * How a siege talks: chat lines led by the siege glyph (U+E019, drawn white because a font glyph
 * carries its own colours), action-bar lines for the moment, and the sounds the mod's other moments
 * use: the raid horn from the Hearth's direction for an announcement, the goat horn for the Siege
 * Horn, world sounds at the Hearth. The per-player wave horn and outcome chime are the siege panel's
 * (client lane), so nothing plays twice. Keys are {@code proficiency.hearth.sieges.*}.
 */
final class SiegeNotices {

    /** The siege glyph of the art contract. */
    static final String GLYPH = "";
    static final String KEY = "proficiency.hearth.sieges.";

    private SiegeNotices() {
    }

    /** A trophy's tier as the stage that won it: "Hall (IV)" (the Stages lane's stage names). */
    static MutableComponent tierName(int tier) {
        int t = Math.max(3, Math.min(6, tier));
        return Component.translatable(KEY + "trophy.tier", Component.translatable("proficiency.hearth.stages.name." + t),
                HearthRules.roman(t));
    }

    /**
     * The sides a wave comes from, one to three compass points (a wave over 6 mobs splits over two
     * gates, over 12 over three): "north", "north and south", "north, south-east and south-west".
     */
    static Component directions(Collection<Integer> sides) {
        List<Component> names = new ArrayList<>();
        for (int side : sides) {
            names.add(Component.translatable(KEY + "dir." + SiegeWaves.DIRECTIONS[side]));
        }
        return switch (names.size()) {
            case 0 -> Component.literal("?");
            case 1 -> names.get(0);
            case 2 -> Component.translatable(KEY + "dir.join2", names.get(0), names.get(1));
            default -> Component.translatable(KEY + "dir.join3", names.get(0), names.get(1), names.get(2));
        };
    }

    static MutableComponent line(String key, ChatFormatting colour, Object... args) {
        return Component.literal(GLYPH + " ").withStyle(ChatFormatting.WHITE)
                .append(Component.translatable(KEY + key, args).withStyle(colour));
    }

    static void chat(Collection<ServerPlayer> players, String key, ChatFormatting colour, Object... args) {
        Component text = line(key, colour, args);
        for (ServerPlayer player : players) {
            player.sendSystemMessage(text);
        }
    }

    static void bar(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
        player.displayClientMessage(Component.translatable(KEY + key, args).withStyle(colour), true);
    }

    static void bar(Collection<ServerPlayer> players, String key, ChatFormatting colour, Object... args) {
        for (ServerPlayer player : players) {
            bar(player, key, colour, args);
        }
    }

    /**
     * The raid horn, heard from the Hearth's direction about 13 blocks off (how a vanilla raid sounds
     * its waves), or at the player when the Hearth is in another dimension.
     */
    static void distantHorn(ServerPlayer player, ServerLevel hearthLevel, BlockPos hearth, float volume) {
        if (player.connection == null) {
            return;
        }
        Vec3 at = player.position();
        double x = at.x;
        double z = at.z;
        if (player.level() == hearthLevel) {
            Vec3 centre = Vec3.atCenterOf(hearth);
            Vec3 dir = new Vec3(centre.x - at.x, 0, centre.z - at.z);
            if (dir.lengthSqr() > 1.0) {
                dir = dir.normalize();
                x = at.x + 13.0 * dir.x;
                z = at.z + 13.0 * dir.z;
            }
        }
        player.connection.send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.NEUTRAL, x, at.y, z,
                volume, 1.0f, player.getRandom().nextLong()));
    }

    static void distantHorn(Collection<ServerPlayer> players, ServerLevel hearthLevel, BlockPos hearth, float volume) {
        for (ServerPlayer player : players) {
            distantHorn(player, hearthLevel, hearth, volume);
        }
    }

    /** The Siege Horn itself: the goat horn's "call", heard far around the player. */
    static void hornCall(ServerPlayer player) {
        hornCall(player, 1);
    }

    /** The goat horn variant and pitch of each horn level: "call" for the first, deeper and darker up to V. */
    private static final int[] HORN_VARIANT = {5, 2, 6, 7, 0};
    private static final float[] HORN_PITCH = {0.9f, 0.85f, 0.8f, 0.72f, 0.62f};

    /** A horn of {@code level} blown: each level has its own call, heard far around the player. */
    static void hornCall(ServerPlayer player, int level) {
        int i = Math.max(1, Math.min(HORN_VARIANT.length, level)) - 1;
        Holder<SoundEvent> sound = SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(
                Math.min(HORN_VARIANT[i], SoundEvents.GOAT_HORN_SOUND_VARIANTS.size() - 1));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound,
                SoundSource.PLAYERS, 8.0f, HORN_PITCH[i]);
    }

    /** A horn's tooltip line: "Siege level III · needs a stage V Hearth (Gathering Place)". */
    public static MutableComponent hornLevelLine(int level) {
        int need = SiegeRules.ceilingForLevel(level);
        return Component.translatable(KEY + "horn.level", HearthRules.roman(level), HearthRules.roman(need),
                Component.translatable("proficiency.hearth.stages.name." + need));
    }

    /**
     * The foes a horn's siege brings that the level below does not: the whole first mix at level I,
     * then what each level adds (pillagers, vindicators and witches, an evoker, a ravager). The shipped
     * mix, so a tooltip needs no server data.
     */
    public static MutableComponent hornFoesLine(int level) {
        HearthData.Siege s = HearthData.DEFAULTS.siege();
        int key = SiegeRules.levelKey(level);
        List<String> ids = new ArrayList<>(SiegeRules.mobsFor(key, s));
        if (level > 1) {
            ids.removeAll(SiegeRules.mobsFor(key - 1, s));
        }
        List<Component> names = new ArrayList<>();
        for (String id : ids) {
            names.add(Component.translatable("entity." + id.replace(':', '.')));
        }
        Component list = names.isEmpty() ? Component.literal("?") : ComponentUtils.formatList(names, Component.literal(", "));
        return Component.translatable(KEY + (level > 1 ? "horn.foes_new" : "horn.foes"), list);
    }

    static void at(ServerLevel level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS,
                volume, pitch);
    }

    static void puff(ServerLevel level, BlockPos pos, ParticleOptions particle, int count, double spread, double speed) {
        level.sendParticles(particle, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, count, spread, 0.4,
                spread, speed);
    }
}
