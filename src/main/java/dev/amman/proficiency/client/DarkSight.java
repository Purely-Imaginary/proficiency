package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.NightwalkerMath;
import dev.amman.proficiency.skill.NightwalkerService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Nightwalker's passive, Dark Sight: while you stand in the dark your eyes adapt, and the world
 * gets a little of Night Vision's light (a tenth at level 100, up to a fifth with talents). It
 * fades in and out over about two seconds, like eyes adjusting.
 *
 * <p>Client only. It works as a partial Night Vision: {@code GameRendererMixin} answers the Night
 * Vision scale with it, which reaches the vanilla light map ({@code LightTextureMixin}) and shader
 * packs through Iris alike. Without the mixins nothing breaks, the passive just does not show.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class DarkSight {

    /** How far the eyes adapt per tick: full in 40 ticks (2 seconds). */
    private static final float STEP = 1.0f / 40.0f;

    private static float current;

    private DarkSight() {
    }

    /** The share of Night Vision's light to add now, 0 when there is none. */
    public static float scale() {
        return current;
    }

    /** The same, for one entity: only the local player has Dark Sight on this screen. */
    public static float scaleFor(net.minecraft.world.entity.Entity entity) {
        return entity != null && entity == Minecraft.getInstance().player ? current : 0f;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        float target = 0f;
        if (player != null && player.level() != null && NightwalkerService.isDark(player)) {
            PlayerSkills skills = ProficiencyAttachments.of(player);
            target = (float) NightwalkerMath.darkSight(skills.bonus(Skill.NIGHTWALKER),
                    TalentService.rank(skills, Skill.NIGHTWALKER, "dark_sight"));
        }
        float before = current;
        if (current < target) {
            current = Math.min(target, current + STEP);
        } else if (current > target) {
            current = Math.max(target, current - STEP);
        }
        if (current != before) {
            requestLightMap();
            if (before == 0f || current == 0f) {
                Proficiency.LOG.debug("Dark Sight {} (target {})", current, target);
            }
        }
    }

    /** Asks for a new light map now, past any light-map cache. Harmless if the accessor is missing. */
    private static void requestLightMap() {
        Object lightTexture = Minecraft.getInstance().gameRenderer.lightTexture();
        if (lightTexture instanceof dev.amman.proficiency.mixin.client.LightTextureAccess access) {
            access.proficiency$setUpdateLightTexture(true);
        }
    }
}
