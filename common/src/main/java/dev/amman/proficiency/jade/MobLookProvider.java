package dev.amman.proficiency.jade;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.look.LookRules;
import dev.amman.proficiency.look.XpLookData;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** What a hit and a kill of the mob under the crosshair would pay the held weapon, in Jade. */
enum MobLookProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    static final ResourceLocation ID = Proficiency.id("look_mob");

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (accessor.getPlayer() instanceof ServerPlayer player) {
            XpLookData.mob(player, accessor.getEntity(), data);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        Skill skill = BlockLookView.skillOf(data);
        if (skill == null) {
            return;
        }
        boolean placed = LookRules.has(data.getInt(XpLookData.FLAGS), LookRules.PLACED);
        if (!placed && SkillIcons.enabled()) {
            BlockLookView.mobPays(tooltip, skill, data, data.getFloat(XpLookData.XP), data.getFloat(XpLookData.KILL), config);
            return;
        }
        BlockLookView.line(tooltip, skill, data, placed ? LookText.mobNothing(skill) : LookText.mob(skill,
                data.getFloat(XpLookData.XP), data.getFloat(XpLookData.KILL)), config);
    }
}
