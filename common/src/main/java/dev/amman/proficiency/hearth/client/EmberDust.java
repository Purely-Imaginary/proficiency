package dev.amman.proficiency.hearth.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The range ring's mote: vanilla's dust (the same {@code generic_7..0} sprites, size, life and drift for a
 * dust scale), drawn at full brightness. Vanilla dust takes the light of the block it is in, so at night
 * the ring at the edge of range, away from the fire's light, was a few dark brown specks on dark ground
 * (polish pass, Forge 1.20.1 real client, 2026-10-10), and night is when a siege comes. In daylight the sky
 * lights dust fully, so the day ring looks as before. Client only: made here and handed to the particle
 * engine, no particle type is registered; on a resource pack without the sprites the caller falls back to
 * plain dust ({@link #spawn} answers false).
 */
final class EmberDust extends TextureSheetParticle {

    /** Full block and sky light, as packed for a vertex ({@code LightTexture.FULL_BRIGHT}). */
    private static final int FULL_BRIGHT = 0xF000F0;
    /** The frames of vanilla's dust, biggest first (assets/minecraft/particles/dust.json). */
    private static final ResourceLocation[] FRAMES = new ResourceLocation[8];

    static {
        for (int i = 0; i < FRAMES.length; i++) {
            FRAMES[i] = ResourceLocation.tryParse("minecraft:generic_" + (7 - i));
        }
    }

    /**
     * The frames last found. A resource reload stitches the particle atlas again in place, with new sprite
     * objects, so one lookup of the first frame tells whether they still hold.
     */
    @Nullable
    private static TextureAtlasSprite[] cached;

    private final TextureAtlasSprite[] frames;

    private EmberDust(ClientLevel level, double x, double y, double z, double ySpeed, Vector3f colour, float scale,
            TextureAtlasSprite[] frames) {
        super(level, x, y, z, 0.0, ySpeed, 0.0);
        // DustParticleBase's numbers, so the ring keeps the look its scale was tuned for.
        this.friction = 0.96F;
        this.speedUpWhenYMotionIsBlocked = true;
        this.frames = frames;
        this.xd *= 0.1F;
        this.yd *= 0.1F;
        this.zd *= 0.1F;
        float shade = this.random.nextFloat() * 0.4F + 0.6F;
        this.rCol = (this.random.nextFloat() * 0.2F + 0.8F) * colour.x() * shade;
        this.gCol = (this.random.nextFloat() * 0.2F + 0.8F) * colour.y() * shade;
        this.bCol = (this.random.nextFloat() * 0.2F + 0.8F) * colour.z() * shade;
        this.quadSize *= 0.75F * scale;
        int life = (int) (8.0 / (this.random.nextDouble() * 0.8 + 0.2));
        this.lifetime = (int) Math.max(life * scale, 1.0F);
        frame();
    }

    /**
     * One mote at {@code (x, y, z)} drifting up at {@code ySpeed}, with the {@code frames} of {@link #frames()}.
     * False when they are missing (a resource pack without the sprites): the caller spawns vanilla dust instead.
     */
    static boolean spawn(ClientLevel level, double x, double y, double z, double ySpeed, Vector3f colour, float scale,
            @Nullable TextureAtlasSprite[] frames) {
        if (frames == null) {
            return false;
        }
        Minecraft.getInstance().particleEngine.add(new EmberDust(level, x, y, z, ySpeed, colour, scale, frames));
        return true;
    }

    /**
     * The eight dust frames from the particle atlas, or null; the ring asks once a pulse and hands them to every
     * mote of it. Kept between pulses while the atlas still holds them (one lookup), found again after a resource
     * reload. The particle atlas is the particle engine's, registered with the texture manager: it is not in the
     * model manager's atlas set, so {@code Minecraft.getTextureAtlas} must not be asked for it (it threw in the
     * first build of this class). Any failure here only costs the full brightness.
     */
    @Nullable
    static TextureAtlasSprite[] frames() {
        try {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
            if (!(texture instanceof TextureAtlas atlas) || FRAMES[0] == null) {
                return null;
            }
            TextureAtlasSprite[] known = cached;
            if (known != null && atlas.getSprite(FRAMES[0]) == known[0]) {
                return known;
            }
            cached = null;
            TextureAtlasSprite[] out = new TextureAtlasSprite[FRAMES.length];
            for (int i = 0; i < FRAMES.length; i++) {
                TextureAtlasSprite sprite = FRAMES[i] == null ? null : atlas.getSprite(FRAMES[i]);
                if (sprite == null || MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name())) {
                    return null;
                }
                out[i] = sprite;
            }
            cached = out;
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void frame() {
        setSprite(frames[Math.min(frames.length - 1, this.age * (frames.length - 1) / Math.max(1, this.lifetime))]);
    }

    @Override
    public void tick() {
        super.tick();
        frame();
    }

    @Override
    public float getQuadSize(float partialTick) {
        return this.quadSize * Mth.clamp((this.age + partialTick) / this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }
}
