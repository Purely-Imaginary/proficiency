package dev.amman.proficiency.jade;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Proficiency's optional Jade plugin. Nothing else in the mod names a Jade class: Jade finds this one
 * itself (the annotation on NeoForge, the {@code jade} entrypoint in fabric.mod.json on Fabric), so
 * without Jade it is never loaded and the mod behaves exactly as before.
 *
 * <p>Four toggles in Jade's own config, under "Proficiency": block XP, mob XP, the first-time mark, and
 * the Hearth. The first three read the server through Jade's data channel, so nothing new rides on the
 * mod's own network and the protocol number does not move.
 */
@WailaPlugin
public class ProficiencyJadePlugin implements IWailaPlugin {

    /** The first-time mark, a toggle with no provider of its own. */
    static final ResourceLocation LOOK_FIRST = Proficiency.id("look_first");
    /** Another player's tooltip (1.36.0): the provider's own id is its toggle in Jade's settings, so no addConfig. */
    static final ResourceLocation LOOK_PLAYERS = Proficiency.id("look_players");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BlockLookProvider.INSTANCE, Block.class);
        registration.registerBlockDataProvider(HearthLookProvider.INSTANCE, HearthBlockEntity.class);
        registration.registerEntityDataProvider(MobLookProvider.INSTANCE, LivingEntity.class);
        registration.registerEntityDataProvider(PlayerLookProvider.INSTANCE, Player.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addConfig(LOOK_FIRST, true);
        registration.registerBlockComponent(BlockLookProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(HearthLookProvider.INSTANCE, HearthBlock.class);
        registration.registerEntityComponent(MobLookProvider.INSTANCE, LivingEntity.class);
        registration.registerEntityComponent(PlayerLookProvider.INSTANCE, Player.class);
    }
}
