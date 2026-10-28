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
 * Proficiency's optional Jade plugin on Forge 1.20.1 (Jade 11). Nothing else in the mod names a Jade class:
 * Jade finds this one by its annotation, so without Jade it is never loaded.
 *
 * <p>Blocks go through {@link LookClient} (Jade 11 has no server data for a block without a block entity);
 * mobs and the Hearth use Jade's own data channel.
 */
@WailaPlugin
public class ProficiencyJadePlugin implements IWailaPlugin {

    /** The first-time mark, a toggle with no provider of its own. */
    static final ResourceLocation LOOK_FIRST = Proficiency.id("look_first");
    /** Another player's tooltip (1.36.0): the provider's own id is its toggle in Jade's settings, so no addConfig. */
    static final ResourceLocation LOOK_PLAYERS = Proficiency.id("look_players");

    @Override
    public void register(IWailaCommonRegistration registration) {
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
