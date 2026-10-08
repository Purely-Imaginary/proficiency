package dev.amman.proficiency.skill;

/**
 * The buffer calls {@link PlayerSkills#write} and {@link PlayerSkills#read} make, named as on
 * Minecraft's {@code FriendlyByteBuf}. Each loader adapts its buffer to these, so the sync packet's
 * byte layout is written once and stays the same on every loader.
 */
public final class SkillsWire {

    private SkillsWire() {
    }

    public interface Out {
        void writeVarInt(int value);

        void writeFloat(float value);

        void writeUtf(String value);

        void writeVarLong(long value);
    }

    public interface In {
        int readVarInt();

        float readFloat();

        String readUtf();

        long readVarLong();
    }
}
